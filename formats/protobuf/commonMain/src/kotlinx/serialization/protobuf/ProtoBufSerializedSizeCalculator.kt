package kotlinx.serialization.protobuf

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.protobuf.internal.*

@OptIn(ExperimentalSerializationApi::class)
internal class ProtoBufSerializedSizeCalculator(
    private val proto: ProtoBuf,
    private val descriptor: SerialDescriptor
) : ProtobufTaggedEncoder() {
    @Suppress("MemberVisibilityCanBePrivate")
    internal var serializedSize = -1 // memoized it

    override val serializersModule: SerializersModule
        get() = proto.serializersModule

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        when (descriptor.kind) {
            SerialKind.CONTEXTUAL -> TODO()
            SerialKind.ENUM -> TODO()
            PolymorphicKind.OPEN -> TODO()
            PolymorphicKind.SEALED -> TODO()
            PrimitiveKind.BOOLEAN -> TODO()
            PrimitiveKind.BYTE -> TODO()
            PrimitiveKind.CHAR -> TODO()
            PrimitiveKind.DOUBLE -> TODO()
            PrimitiveKind.FLOAT -> TODO()
            PrimitiveKind.INT -> TODO()
            PrimitiveKind.LONG -> TODO()
            PrimitiveKind.SHORT -> TODO()
            PrimitiveKind.STRING -> TODO()
            StructureKind.CLASS -> TODO()
            StructureKind.LIST -> TODO()
            StructureKind.MAP -> TODO()
            StructureKind.OBJECT -> TODO()
        }
    }

    override fun SerialDescriptor.getTag(index: Int): ProtoDesc = extractParameters(index)

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