package kotlinx.serialization.protobuf

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.protobuf.internal.*
import kotlin.jvm.JvmField

// notes: memoization can probably be done with a concurrent map holding descriptor and serializedSize.

@OptIn(ExperimentalSerializationApi::class)
internal open class ProtoBufSerializedSizeCalculator(
    private val proto: ProtoBuf,
    private val descriptor: SerialDescriptor
) : ProtobufTaggedEncoder() {
    @Suppress("MemberVisibilityCanBePrivate")
    internal var serializedSize = -1 // memoized it

    override val serializersModule: SerializersModule
        get() = proto.serializersModule

    override fun shouldEncodeElementDefault(descriptor: SerialDescriptor, index: Int): Boolean = proto.encodeDefaults

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
                if (tag == MISSING_TAG && descriptor == this.descriptor) this
                else ObjectSizeCalculator(proto, currentTagOrDefault, descriptor)
            }

            StructureKind.MAP -> MapRepeatedCalculator(proto, currentTagOrDefault, descriptor)
            else -> throw SerializationException("This serial kind is not supported as structure: $descriptor")
        }
    }

    override fun endEncode(descriptor: SerialDescriptor) {
        TODO("update here the serialized size")
    }

    override fun SerialDescriptor.getTag(index: Int): ProtoDesc = extractParameters(index)

    override fun <T> encodeSerializableValue(serializer: SerializationStrategy<T>, value: T) {
        TODO("")
    }

    override fun encodeTaggedInt(tag: ProtoDesc, value: Int) {
        requireNotMissingTag(tag)
        serializedSize += computeIntSize(value, tag.protoId, tag.integerType)
    }

    override fun encodeTaggedLong(tag: ProtoDesc, value: Long) {
        requireNotMissingTag(tag)
        serializedSize += computeLongSize(value, tag.protoId, tag.integerType)
    }

    override fun encodeTaggedByte(tag: ProtoDesc, value: Byte) {
        requireNotMissingTag(tag)
        serializedSize += computeIntSize(value.toInt(), tag.protoId, tag.integerType)
    }

    override fun encodeTaggedShort(tag: ProtoDesc, value: Short) {
        requireNotMissingTag(tag)
        serializedSize += computeIntSize(value.toInt(), tag.protoId, tag.integerType)
    }

    override fun encodeTaggedFloat(tag: ProtoDesc, value: Float) {
        requireNotMissingTag(tag)
        serializedSize += computeFloatSize(tag.protoId)
    }

    override fun encodeTaggedDouble(tag: ProtoDesc, value: Double) {
        requireNotMissingTag(tag)
        serializedSize += computeDoubleSize(tag.protoId)
    }

    override fun encodeTaggedBoolean(tag: ProtoDesc, value: Boolean) {
        requireNotMissingTag(tag)
        serializedSize += computeBooleanSize(tag.protoId)
    }

    override fun encodeTaggedChar(tag: ProtoDesc, value: Char) {
        requireNotMissingTag(tag)
        serializedSize += computeIntSize(value.code, tag.protoId, tag.integerType)
    }

    override fun encodeTaggedString(tag: ProtoDesc, value: String) {
        requireNotMissingTag(tag)
        serializedSize += computeStringSize(value, tag.protoId)
    }

    override fun encodeTaggedEnum(tag: ProtoDesc, enumDescriptor: SerialDescriptor, ordinal: Int) {
        requireNotMissingTag(tag)
        serializedSize += computeEnumSize(
            extractProtoId(enumDescriptor, ordinal, zeroBasedDefault = true),
            tag.protoId,
            ProtoIntegerType.DEFAULT
        )
    }

    internal fun encodeByteArray(value: ByteArray) {
        val tag = popTagOrDefault()
        requireNotMissingTag(tag)
        serializedSize += computeByteArraySize(value, tag.protoId)
    }

    /*
     * Maybe instead of aborting is ok to just return?
     */
    private fun requireNotMissingTag(tag: ProtoDesc) {
        if (tag == MISSING_TAG) throw SerializationException("tag for: $tag is required")
    }
}

@OptIn(ExperimentalSerializationApi::class)
internal open class ObjectSizeCalculator(
    proto: ProtoBuf,
    @Suppress("unused") @JvmField protected val parentTag: ProtoDesc,
    descriptor: SerialDescriptor
) : ProtoBufSerializedSizeCalculator(proto, descriptor)

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
private class RepeatedCalculator(
    proto: ProtoBuf,
    @JvmField val curTag: ProtoDesc,
    descriptor: SerialDescriptor
) : ObjectSizeCalculator(proto, curTag, descriptor) {
    override fun SerialDescriptor.getTag(index: Int) = curTag
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
    return when (format) {
        ProtoIntegerType.DEFAULT -> tagSize + computeInt64SizeNoTag(value)
        ProtoIntegerType.SIGNED -> tagSize + computeSInt64SizeNoTag(value)
        ProtoIntegerType.FIXED -> tagSize + getFixed64SizeNoTag()
    }
}

@OptIn(ExperimentalSerializationApi::class)
private fun computeIntSize(value: Int, tag: Int, format: ProtoIntegerType): Int {
    val tagSize = computeTagSize(tag)
    return when (format) {
        //TODO: ProtobufWriter actually serializes default as varint64, should we align?
        ProtoIntegerType.DEFAULT -> tagSize + computeInt32SizeNoTag(value)
        ProtoIntegerType.SIGNED -> tagSize + computeSInt32SizeNoTag(value)
        ProtoIntegerType.FIXED -> tagSize + getFixed32SizeNoTag()
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
private fun computeEnumSize(value: Int, tag: Int, format: ProtoIntegerType): Int = computeIntSize(value, tag, format)

private fun computeByteArraySize(value: ByteArray, tag: Int): Int {
    val tagSize = computeTagSize(tag)
    return tagSize + computeByteArraySizeNoTag(value)
}