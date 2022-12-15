package kotlinx.serialization.protobuf

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.internal.MapLikeSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.protobuf.internal.*
import kotlin.jvm.JvmField

//TODO: needs @ThreadLocal?
private val memoizedSerializedSizes = createSerializedSizeCache()

internal expect fun createSerializedSizeCache(): SerializedSizeCache

//TODO: add kdoc
// notes: memoization can probably be done with a concurrent map holding descriptor and serializedSize.
internal interface SerializedSizeCache {
    fun get(key: SerialDescriptor): Int?
    fun put(key: SerialDescriptor, size: Int)
}

//TODO: probably this is better to be put in diff kt file.
@OptIn(ExperimentalSerializationApi::class)
public fun <T> ProtoBuf.getOrComputeSerializedSize(serializer: SerializationStrategy<T>, value: T): Int {
    val memoizedSize = memoizedSerializedSizes.get(serializer.descriptor)
    return if (memoizedSize != null) {
        memoizedSize
    } else {
        val calculator = ProtoBufSerializedSizeCalculator(this, serializer.descriptor)
        calculator.encodeSerializableValue(serializer, value)
        calculator.serializedSize
    }
}

@OptIn(ExperimentalSerializationApi::class)
internal open class ProtoBufSerializedSizeCalculator(
    private val proto: ProtoBuf,
    private val descriptor: SerialDescriptor
) : ProtobufTaggedEncoder() {
    internal var serializedSize = -1 // memoized it

    override val serializersModule: SerializersModule
        get() = proto.serializersModule

    override fun shouldEncodeElementDefault(descriptor: SerialDescriptor, index: Int): Boolean = proto.encodeDefaults

    override fun beginCollection(descriptor: SerialDescriptor, collectionSize: Int): CompositeEncoder =
        when (descriptor.kind) {
            StructureKind.LIST -> {
                val tag = currentTagOrDefault
                if (tag.isPacked && descriptor.getElementDescriptor(0).isPackable) {
                    TODO("not yet implemented")
                } else {
                    if (tag == MISSING_TAG) {
                        serializedSize += collectionSize
                    }
                    if (this.descriptor.kind == StructureKind.LIST && tag != MISSING_TAG && this.descriptor != descriptor) {
                        TODO("not yet implemented")
                    } else {
                        println("inside repeatedCalculator")
                        println("size at this moment: $serializedSize") // encoding of int is ok
                        RepeatedCalculator(proto, tag, descriptor)
                    }
                }
            }

            StructureKind.MAP -> TODO("not yet implemented")
            else -> throw SerializationException("This serial kind is not supported as collection: $descriptor")
        }

    /* TODO proper impl */
    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        serializedSize = 0 // reset serialized-size
        // delegate to proper calculator, e.g. class,map,list
        return when (descriptor.kind) {
            StructureKind.LIST -> {
                if (descriptor.getElementDescriptor(0).isPackable && currentTagOrDefault.isPacked) {
                    PackedArrayCalculator(proto, currentTagOrDefault, descriptor)
                } else {
                    RepeatedCalculator(proto, currentTagOrDefault, descriptor)
                }
            }

            StructureKind.CLASS, StructureKind.OBJECT, is PolymorphicKind -> {
                val tag = currentTagOrDefault
                if (tag == MISSING_TAG && descriptor == this.descriptor) this //TODO: which exactly is this scenario?
                else ObjectSizeCalculator(proto, currentTagOrDefault, descriptor)
            }

            StructureKind.MAP -> MapRepeatedCalculator(proto, currentTagOrDefault, descriptor)
            else -> throw SerializationException("This serial kind is not supported as structure: $descriptor")
        }
    }

    override fun endEncode(descriptor: SerialDescriptor) {
        memoizedSerializedSizes.put(descriptor, serializedSize)
    }

    override fun SerialDescriptor.getTag(index: Int): ProtoDesc = extractParameters(index)

    override fun <T> encodeSerializableValue(serializer: SerializationStrategy<T>, value: T) {
        return when {
            serializer is MapLikeSerializer<*, *, *, *> -> {
                TODO("needs research.")
            }

            serializer.descriptor == ByteArraySerializer().descriptor -> computeByteArraySize(value as ByteArray)

            //TODO: do we need to check here for list types?
            serializer.descriptor.kind !is StructureKind.LIST &&
                    serializer.descriptor != this.descriptor -> computeMessageSize(serializer, value)


            else -> serializer.serialize(this, value)
        }
    }

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
        if (tag == MISSING_TAG) {
            TODO("check how is genereated")
        } else {
            serializedSize += computeEnumSize(
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
        val tag = popTagOrDefault()
        //requireNotMissingTag(tag)
        //TODO check how is generated
        serializedSize += proto.computeMessageSize(serializer, value, tag.protoId)
    }
}

@OptIn(ExperimentalSerializationApi::class)
internal open class ObjectSizeCalculator(
    proto: ProtoBuf,
    @Suppress("unused") @JvmField protected val parentTag: ProtoDesc,
    descriptor: SerialDescriptor
) : ProtoBufSerializedSizeCalculator(proto, descriptor)

@OptIn(ExperimentalSerializationApi::class)
private class RepeatedCalculator(
    proto: ProtoBuf,
    @JvmField val curTag: ProtoDesc,
    descriptor: SerialDescriptor
) : ObjectSizeCalculator(proto, curTag, descriptor) {
    override fun SerialDescriptor.getTag(index: Int) = curTag
}

@OptIn(ExperimentalSerializationApi::class)
private class MapRepeatedCalculator(
    proto: ProtoBuf,
    parentTag: ProtoDesc,
    descriptor: SerialDescriptor
) : ObjectSizeCalculator(proto, parentTag, descriptor) {
    override fun SerialDescriptor.getTag(index: Int): ProtoDesc =
        if (index % 2 == 0) ProtoDesc(1, (parentTag.integerType))
        else ProtoDesc(2, (parentTag.integerType))
}

@OptIn(ExperimentalSerializationApi::class)
internal open class NestedRepeatedCalculator(
    proto: ProtoBuf,
    @JvmField val curTag: ProtoDesc,
    descriptor: SerialDescriptor,
) : ObjectSizeCalculator(proto, curTag, descriptor) {
    // all elements always have id = 1
    override fun SerialDescriptor.getTag(index: Int) = ProtoDesc(1, ProtoIntegerType.DEFAULT)
}

/* TODO */
// Is missing_tag case only for packed messages?
@OptIn(ExperimentalSerializationApi::class)
internal class PackedArrayCalculator(
    proto: ProtoBuf,
    curTag: ProtoDesc,
    descriptor: SerialDescriptor,
) : ObjectSizeCalculator(proto, curTag, descriptor) {

    // Triggers not writing header
    override fun SerialDescriptor.getTag(index: Int): ProtoDesc = MISSING_TAG

    override fun endEncode(descriptor: SerialDescriptor) {
        TODO()
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

// helpers

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

private fun computeBooleanSize(tag: Int): Int {
    val tagSize = computeTagSize(tag)
    return tagSize + 1
}

private fun computeStringSize(value: String, tag: Int): Int {
    val tagSize = computeTagSize(tag)
    return tagSize + computeStringSizeNoTag(value)
}

@OptIn(ExperimentalSerializationApi::class)
private fun computeEnumSize(value: Int, tag: Int, format: ProtoIntegerType): Int {
    val tagSize = computeTagSize(tag)
    return tagSize + computeIntSize(value, tag, format)
}

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
    val memoizedSize = memoizedSerializedSizes.get(serializer.descriptor)
    return if (memoizedSize != null) {
        memoizedSize
    } else {
        val calculator = ProtoBufSerializedSizeCalculator(this, serializer.descriptor)
        calculator.encodeSerializableValue(serializer, value)
        calculator.serializedSize
    }
}