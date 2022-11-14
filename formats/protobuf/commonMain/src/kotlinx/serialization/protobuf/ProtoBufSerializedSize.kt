package kotlinx.serialization.protobuf

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.MapEntrySerializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.internal.MapLikeSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.protobuf.internal.*
import kotlin.jvm.JvmField

//TODO: needs @ThreadLocal?
private val memoizedSerializedSizes = createSerializedSizeCache()

internal expect fun createSerializedSizeCache(): SerializedSizeCache

/**
 * The object's hashcode, acting as a key for associating an instance of class with a given [SerialDescriptor].
 */
internal typealias SerializedSizeCacheKey = Int

internal typealias SerializedData = Map<SerializedSizeCacheKey, Int>

/**
 * A storage to memoize a computed `serializedSize`.
 */
// Notes: js-impl & native-impl are not based on concurrent-safe structures.
internal interface SerializedSizeCache {
    /**
     * Returns the `serializedSize` associated with the given [key] and [descriptor], if found else null.
     */
    operator fun get(descriptor: SerialDescriptor, key: SerializedSizeCacheKey): Int?

    /**
     * Sets the `serializedSize` and associates it with the given [key] and [descriptor].
     */
    operator fun set(descriptor: SerialDescriptor, key: SerializedSizeCacheKey, serializedSize: Int)
}

internal fun SerializedSizeCache.getOrPut(
    descriptor: SerialDescriptor,
    key: SerializedSizeCacheKey,
    defaultValue: () -> Int
): Int {
    get(descriptor, key)?.let { return it }
    val value = defaultValue()
    set(descriptor, key, value)
    return value
}

/**
 * Returns the number of bytes required to encode this [message][value]. The size is computed on the first call
 * and memoized.
 */
// Notes: Even though this API has some usage for consumers, for example see: https://github.com/protobufjs/protobuf.js/issues/162
// It can also be considered as an internal API, to support only encoding/decoding delimited-messages.
@ExperimentalSerializationApi
public fun <T> ProtoBuf.getOrComputeSerializedSize(serializer: SerializationStrategy<T>, value: T): Int {
    val key = value.hashCode()
    return memoizedSerializedSizes.getOrPut(serializer.descriptor, key) {
        val calculator = ProtoBufSerializedSizeCalculator(this, serializer.descriptor)
        calculator.encodeSerializableValue(serializer, value)
        calculator.serializedSize
    }
}

/**
 * Internal helper to pass around serialized size.
 * Should not be leaked to the outside world.
 */
internal data class SerializedSizePointer(var value: Int)

// alternative name: ProtoBufSerializedSizeComputor
@ExperimentalSerializationApi
internal open class ProtoBufSerializedSizeCalculator(
    private val proto: ProtoBuf,
    internal val descriptor: SerialDescriptor,
    private val serializedSizePointer: SerializedSizePointer = SerializedSizePointer(-1)
) : ProtobufTaggedEncoder() {
    internal var serializedSize
        get() = serializedSizePointer.value
        set(value) {
            println("updating size with $value for ${descriptor.serialName}")
            serializedSizePointer.value = value
        }

    override val serializersModule: SerializersModule get() = proto.serializersModule

    override fun shouldEncodeElementDefault(descriptor: SerialDescriptor, index: Int): Boolean = proto.encodeDefaults

    override fun beginCollection(descriptor: SerialDescriptor, collectionSize: Int): CompositeEncoder {
        println("\n beginning collection for:$descriptor \n")
        return when (descriptor.kind) {
            StructureKind.LIST -> {
                val tag = currentTagOrDefault
                if (tag.isPacked && descriptor.getElementDescriptor(0).isPackable) {
                    println("in PackedArrayCalculator")
                    PackedArrayCalculator(proto, currentTagOrDefault, descriptor, serializedSizePointer)
                } else {
                    if (serializedSize == -1) serializedSize = 0
                    println("tag state:$tag")
                    if (this.descriptor.kind == StructureKind.LIST && tag != MISSING_TAG && this.descriptor != descriptor) {
                        // NestedRepeatedEncoder
                        // Never reaching here. Not sure which case it falls into.
                        TODO("research if it is needed at all")
                    } else {
                        println("before: RepeatedCalculator")
                        if (this is RepeatedCalculator) {
                            println("returning this RepeatedCalculator")
                            this
                        } else {
                            println("current $serializedSize")
                            println("returning new RepeatedCalculator")
                            RepeatedCalculator(proto, tag, descriptor, serializedSizePointer)
                        }
                    }
                }
            }

            StructureKind.MAP -> MapRepeatedCalculator(proto, currentTagOrDefault, descriptor, serializedSizePointer)
            else -> throw SerializationException("This serial kind is not supported as collection: $descriptor")
        }
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        println("\n Beginning structure\n")
        if (serializedSize == -1) serializedSize = 0
        // delegate to proper calculator, e.g. class, map, list, etc.
        return when (descriptor.kind) {
            StructureKind.LIST -> {
                println("\n --- inside struct List --- \n")
                if (descriptor.getElementDescriptor(0).isPackable && currentTagOrDefault.isPacked) {
                    PackedArrayCalculator(proto, currentTagOrDefault, descriptor, serializedSizePointer)
                } else {
                    RepeatedCalculator(proto, currentTagOrDefault, descriptor, serializedSizePointer)
                }
            }

            StructureKind.CLASS, StructureKind.OBJECT, is PolymorphicKind -> {
                println("--- inside struct class ---")
                this
            }

            StructureKind.MAP -> {
                println("--- inside struct map ---")
                MapRepeatedCalculator(proto, currentTagOrDefault, descriptor, serializedSizePointer)
            }

            else -> throw SerializationException("This serial kind is not supported as structure: $descriptor")
        }
    }

    override fun endEncode(descriptor: SerialDescriptor) {}

    override fun SerialDescriptor.getTag(index: Int): ProtoDesc = extractParameters(index)

    override fun <T> encodeSerializableValue(serializer: SerializationStrategy<T>, value: T) {
        println("\n ---- encodeSerializableValue for ${serializer.descriptor} ---- \n")
        return when {
            serializer is MapLikeSerializer<*, *, *, *> -> {
                println("going to compute mapSize")
                computeMapSize(serializer as SerializationStrategy<T>, value)
            }

            serializer.descriptor == ByteArraySerializer().descriptor -> computeByteArraySize(value as ByteArray)

            // This path is specifically only for computing size of "Messages" (objects).
            (serializer.descriptor.kind is StructureKind.CLASS ||
                    serializer.descriptor.kind is PolymorphicKind ||
                    serializer.descriptor.kind is StructureKind.OBJECT) &&
                    serializer.descriptor.kind !is PrimitiveKind &&
                    serializer.descriptor != this.descriptor -> {
                println(
                    "--- computing messageSize with desc:${serializer.descriptor} ---"
                )
                computeMessageSize(serializer, value)
            }

            serializer.descriptor != this.descriptor &&
                    serializer.descriptor.kind is StructureKind.LIST &&
                    serializer.descriptor.isChildDescriptorPrimitive() &&
                    serializer.descriptor.isNotPacked() // packed fields are computed through different path.
            -> {
                println("going to compute repeatedPrimitive")
                computeRepeatedPrimitive(serializer, value)
            }

            serializer.descriptor != this.descriptor &&
                    serializer.descriptor.kind is StructureKind.LIST &&
                    // ensure child is not primitive, since repeated primitives are computed through different path.
                    serializer.descriptor.isNotChildDescriptorPrimitive()
            -> {
                println("going to compute repeatedMessage")
                computeRepeatedMessageSize(serializer, value)
            }


            else -> {
                println("--- inside serializer.serialize with desc:${serializer.descriptor} ---")
                serializer.serialize(this, value)
            }
        }
    }

    private fun SerialDescriptor.isNotPacked(): Boolean =
        !(getElementDescriptor(0).isPackable && currentTagOrDefault.isPacked)

    override fun encodeTaggedInt(tag: ProtoDesc, value: Int) {
        serializedSize += if (tag == MISSING_TAG) {
            computeIntSizeNoTag(value, tag.integerType)
        } else {
            computeIntSize(value, tag.protoId, tag.integerType)
        }
    }

    override fun encodeTaggedLong(tag: ProtoDesc, value: Long) {
        serializedSize += if (tag == MISSING_TAG) {
            computeLongSizeNoTag(value, tag.integerType)
        } else {
            computeLongSize(value, tag.protoId, tag.integerType)
        }
    }

    override fun encodeTaggedByte(tag: ProtoDesc, value: Byte) {
        serializedSize += if (tag == MISSING_TAG) {
            computeIntSizeNoTag(value.toInt(), tag.integerType)
        } else {
            computeIntSize(value.toInt(), tag.protoId, tag.integerType)
        }

    }

    override fun encodeTaggedShort(tag: ProtoDesc, value: Short) {
        serializedSize += if (tag == MISSING_TAG) {
            computeIntSizeNoTag(value.toInt(), tag.integerType)
        } else {
            computeIntSize(value.toInt(), tag.protoId, tag.integerType)
        }
    }

    override fun encodeTaggedFloat(tag: ProtoDesc, value: Float) {
        serializedSize += if (tag == MISSING_TAG) {
            getFixed32SizeNoTag()
        } else {
            computeFloatSize(tag.protoId)
        }
    }

    override fun encodeTaggedDouble(tag: ProtoDesc, value: Double) {
        serializedSize += if (tag == MISSING_TAG) {
            getFixed64SizeNoTag()
        } else {
            computeDoubleSize(tag.protoId)
        }
    }

    override fun encodeTaggedBoolean(tag: ProtoDesc, value: Boolean) {
        serializedSize += if (tag == MISSING_TAG) {
            1
        } else {
            computeBooleanSize(tag.protoId)
        }
    }

    override fun encodeTaggedChar(tag: ProtoDesc, value: Char) {
        serializedSize += if (tag == MISSING_TAG) {
            computeIntSizeNoTag(value.code, tag.integerType)
        } else {
            computeIntSize(value.code, tag.protoId, tag.integerType)
        }
    }

    override fun encodeTaggedString(tag: ProtoDesc, value: String) {
        serializedSize += if (tag == MISSING_TAG) {
            computeStringSizeNoTag(value)
        } else {
            computeStringSize(value, tag.protoId)
        }
    }

    override fun encodeTaggedEnum(tag: ProtoDesc, enumDescriptor: SerialDescriptor, ordinal: Int) {
        println("in encodeTaggedEnum for tag: $tag")
        serializedSize += if (tag == MISSING_TAG) {
            computeEnumSizeNoTag(extractProtoId(enumDescriptor, ordinal, zeroBasedDefault = true))
        } else {
            computeEnumSize(
                extractProtoId(enumDescriptor, ordinal, zeroBasedDefault = true),
                tag.protoId,
                ProtoIntegerType.DEFAULT
            )
        }
    }

    private fun computeByteArraySize(value: ByteArray) {
        val tag = popTagOrDefault()
        serializedSize += if (tag == MISSING_TAG) {
            computeByteArraySizeNoTag(value)
        } else {
            computeByteArraySize(value, tag.protoId)
        }
    }

    private fun <T> computeMessageSize(serializer: SerializationStrategy<T>, value: T) {
        val tag = currentTagOrDefault
        println("\n ---- tag:$tag in computeMessageSize ---- \n")
        val size = proto.computeMessageSize(serializer, value, tag.protoId)
        println("result: $size for ${serializer.descriptor}")
        serializedSize += size
    }

    private fun <T> computeRepeatedMessageSize(serializer: SerializationStrategy<T>, value: T) {
        println("inside computeRepeatedObject with desc: ${serializer.descriptor} and tag:$currentTagOrDefault")
        val tag = popTag() // tag is required for calculating repeated objects
        // each object in collection should be calculated with the same tag.
        val calculator = RepeatedCalculator(proto, tag, serializer.descriptor)
        calculator.encodeSerializableValue(serializer, value)
        serializedSize += calculator.serializedSize
    }

    private fun <T> computeRepeatedPrimitive(serializer: SerializationStrategy<T>, value: T) {
        println("inside computeRepeatedPrimitive with desc: ${serializer.descriptor}")
        val calculator = PrimitiveRepeatedCalculator(proto, currentTagOrDefault, serializer.descriptor)
        calculator.encodeSerializableValue(serializer, value)
        serializedSize += calculator.serializedSize
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> computeMapSize(serializer: SerializationStrategy<T>, value: T) {
        // encode maps as collection of map entries, not merged collection of key-values
        println("\n ---- encoding as Map with tag: ${currentTag}---- \n")
        val casted = (serializer as MapLikeSerializer<Any?, Any?, T, *>)
        val mapEntrySerial = MapEntrySerializer(casted.keySerializer, casted.valueSerializer)
        val entries = (value as Map<*, *>).entries
        // calculate each entry separately through computeMessageSize(). We do not need to use computeRepeatedMessageSize(),
        // as we already have our message (entry) and there is no need to unwrap the collection.
        for (entry in entries) computeMessageSize(mapEntrySerial, entry)
    }
}

@ExperimentalSerializationApi
private open class ObjectSizeCalculator(
    proto: ProtoBuf,
    @JvmField protected val parentTag: ProtoDesc,
    descriptor: SerialDescriptor,
    serializedSizePointer: SerializedSizePointer = SerializedSizePointer(-1)
) : ProtoBufSerializedSizeCalculator(proto, descriptor, serializedSizePointer)

@ExperimentalSerializationApi
private open class RepeatedCalculator(
    proto: ProtoBuf,
    @JvmField val curTag: ProtoDesc,
    descriptor: SerialDescriptor,
    serializedWrapper: SerializedSizePointer = SerializedSizePointer(-1)
) : ObjectSizeCalculator(proto, curTag, descriptor, serializedWrapper) {
    init {
        if (serializedSize == -1) serializedSize = 0
    }

    override fun SerialDescriptor.getTag(index: Int) = curTag
}

/*
 * Helper class to compute repeated primitives. The mental model is similar to this:
 * tagSize = computeTagSize(tag)
 * size = tagSize + computeElementSizeNoTag(type, value)
 *
 * To compute size we need 2 things;
 * 1) compute elements without their tag.
 * 2) compute tags for every element separately.
 */
@ExperimentalSerializationApi
private class PrimitiveRepeatedCalculator(
    proto: ProtoBuf,
    // The actual tag of field.
    curTag: ProtoDesc,
    descriptor: SerialDescriptor,
    serializedSizePointer: SerializedSizePointer = SerializedSizePointer(-1)
) : RepeatedCalculator(proto, curTag, descriptor, serializedSizePointer) {

    // Triggers computers to choose `MISSING_TAG` path
    override fun SerialDescriptor.getTag(index: Int): ProtoDesc = MISSING_TAG

    /*
     * Compute tagSize for every primitive and then delegate computing.
     */

    override fun encodeTaggedBoolean(tag: ProtoDesc, value: Boolean) {
        if (curTag != MISSING_TAG) serializedSize += computeTagSize(curTag.protoId)
        super.encodeTaggedBoolean(tag, value)
    }

    override fun encodeTaggedByte(tag: ProtoDesc, value: Byte) {
        if (curTag != MISSING_TAG) serializedSize += computeTagSize(curTag.protoId)
        super.encodeTaggedByte(tag, value)
    }

    override fun encodeTaggedInt(tag: ProtoDesc, value: Int) {
        if (curTag != MISSING_TAG) serializedSize += computeTagSize(curTag.protoId)
        super.encodeTaggedInt(tag, value)
    }

    override fun encodeTaggedLong(tag: ProtoDesc, value: Long) {
        if (curTag != MISSING_TAG) serializedSize += computeTagSize(curTag.protoId)
        super.encodeTaggedLong(tag, value)
    }

    override fun encodeTaggedShort(tag: ProtoDesc, value: Short) {
        if (curTag != MISSING_TAG) serializedSize += computeTagSize(curTag.protoId)
        super.encodeTaggedShort(tag, value)
    }
}

@ExperimentalSerializationApi
private class MapRepeatedCalculator(
    proto: ProtoBuf,
    parentTag: ProtoDesc,
    descriptor: SerialDescriptor,
    serializedSizePointer: SerializedSizePointer
) : ObjectSizeCalculator(proto, parentTag, descriptor, serializedSizePointer) {
    init {
        if (serializedSize == -1) serializedSize = 0
    }

    override fun SerialDescriptor.getTag(index: Int): ProtoDesc =
        if (index % 2 == 0) ProtoDesc(1, (parentTag.integerType))
        else ProtoDesc(2, (parentTag.integerType))
}

@OptIn(ExperimentalSerializationApi::class)
private open class NestedRepeatedCalculator(
    proto: ProtoBuf,
    @JvmField val curTag: ProtoDesc,
    descriptor: SerialDescriptor,
    serializedSizePointer: SerializedSizePointer
) : ObjectSizeCalculator(proto, curTag, descriptor, serializedSizePointer) {
    init {
        if (serializedSize == -1) serializedSize = 0
    }

    // all elements always have id = 1
    override fun SerialDescriptor.getTag(index: Int) = ProtoDesc(1, ProtoIntegerType.DEFAULT)

}

@OptIn(ExperimentalSerializationApi::class)
private class PackedArrayCalculator(
    proto: ProtoBuf,
    curTag: ProtoDesc,
    descriptor: SerialDescriptor,
    // Parent size to be updated after computing the size.
    private val parentSerializedSize: SerializedSizePointer
) : NestedRepeatedCalculator(
    proto,
    curTag,
    descriptor,
    /* SerializedSize to be used as result container. The final tag is computed through this result. */
    SerializedSizePointer(-1)
) {
    // Triggers computers to choose `MISSING_TAG` path
    override fun SerialDescriptor.getTag(index: Int): ProtoDesc = MISSING_TAG

    override fun endEncode(descriptor: SerialDescriptor) {
        if (serializedSize == 0) return // empty collection
        println("protoId: ${curTag.protoId}")
        serializedSize += computeUInt32SizeNoTag(serializedSize) // compute varint based on result of "serializedSize".
        println("serializedSize after varint field number:${computeUInt32SizeNoTag(curTag.protoId)}")
        // Since repeated fields are encoded as single LEN record that contains each element concatenated, then tag
        // should be computed once for whole message.
        val tag = computeTagSize(curTag.protoId)
        parentSerializedSize.value += tag + serializedSize // update parentSize
    }

    override fun beginCollection(descriptor: SerialDescriptor, collectionSize: Int): CompositeEncoder {
        throw SerializationException("Packing only supports primitive number types")
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        throw SerializationException("Packing only supports primitive number types")
    }

    override fun encodeTaggedString(tag: ProtoDesc, value: String) {
        throw SerializationException("Packing only supports primitive number types")
    }
}

// computers

@OptIn(ExperimentalSerializationApi::class)
private fun computeLongSize(value: Long, tag: Int, format: ProtoIntegerType): Int {
    val tagSize = computeTagSize(tag)
    return tagSize + computeLongSizeNoTag(value, format)
}

@OptIn(ExperimentalSerializationApi::class)
private fun computeLongSizeNoTag(value: Long, format: ProtoIntegerType): Int {
    return when (format) {
        ProtoIntegerType.DEFAULT -> computeInt64SizeNoTag(value)
        ProtoIntegerType.SIGNED -> computeSInt64SizeNoTag(value)
        ProtoIntegerType.FIXED -> getFixed64SizeNoTag()
    }
}

@OptIn(ExperimentalSerializationApi::class)
private fun computeIntSize(value: Int, tag: Int, format: ProtoIntegerType): Int {
    val tagSize = computeTagSize(tag)
    return tagSize + computeIntSizeNoTag(value, format)
}

@OptIn(ExperimentalSerializationApi::class)
private fun computeIntSizeNoTag(value: Int, format: ProtoIntegerType): Int {
    return when (format) {
        //TODO: ProtobufWriter actually serializes default as varint64, should we align?
        ProtoIntegerType.DEFAULT -> computeInt32SizeNoTag(value)
        ProtoIntegerType.SIGNED -> computeSInt32SizeNoTag(value)
        ProtoIntegerType.FIXED -> getFixed32SizeNoTag()
    }
}

private fun computeFloatSize(tag: Int): Int {
    val tagSize = computeTagSize(tag)
    // floats have wire type of `I32` which means the size is fixed
    return tagSize + getFixed32SizeNoTag()
}

private fun computeDoubleSize(tag: Int): Int {
    val tagSize = computeTagSize(tag)
    // doubles have wire type of `I64` which means the size is fixed
    return tagSize + getFixed64SizeNoTag()
}

/*
 * Booleans encode as either `00` or `01`, per proto-spec.
 */
private fun computeBooleanSize(tag: Int): Int {
    val tagSize = computeTagSize(tag)
    return tagSize + 1
}

private fun computeStringSize(value: String, tag: Int): Int {
    val tagSize = computeTagSize(tag)
    return tagSize + computeStringSizeNoTag(value)
}

/*
 * Enums are encoded as `int32` per proto-spec.
 */
@OptIn(ExperimentalSerializationApi::class)
private fun computeEnumSize(value: Int, tag: Int, format: ProtoIntegerType): Int = computeIntSize(value, tag, format)

private fun computeByteArraySize(value: ByteArray, tag: Int): Int {
    val tagSize = computeTagSize(tag)
    return tagSize + computeByteArraySizeNoTag(value)
}

@OptIn(ExperimentalSerializationApi::class)
private fun <T> ProtoBuf.computeMessageSize(
    serializer: SerializationStrategy<T>,
    value: T,
    tag: Int
): Int {
    val tagSize = computeTagSize(tag)
    return tagSize + computeMessageSizeNoTag(serializer, value)
}

@OptIn(ExperimentalSerializationApi::class)
private fun <T> ProtoBuf.computeMessageSizeNoTag(serializer: SerializationStrategy<T>, value: T): Int =
    computeLengthDelimitedFieldSize(computeSerializedMessageSize(serializer, value))

@OptIn(ExperimentalSerializationApi::class)
private fun <T> ProtoBuf.computeSerializedMessageSize(serializer: SerializationStrategy<T>, value: T): Int {
    val calculator = ProtoBufSerializedSizeCalculator(this, serializer.descriptor)
    println("calculating size for ${serializer.descriptor}")
    calculator.encodeSerializableValue(serializer, value)
    println("calculator.serializedSize: ${calculator.serializedSize}")
    return calculator.serializedSize
}

// helpers

@OptIn(ExperimentalSerializationApi::class)
private fun SerialDescriptor.isChildDescriptorPrimitive(): Boolean {
    val child = runCatching { this.getElementDescriptor(0) }.getOrElse { return false }
    return child.kind is PrimitiveKind
}

private fun SerialDescriptor.isNotChildDescriptorPrimitive(): Boolean = !isChildDescriptorPrimitive()