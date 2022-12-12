package kotlinx.serialization.protobuf.internal

internal fun computeByteArraySizeNoTag(value: ByteArray): Int = computeLengthDelimitedFieldSize(value.size)

internal fun computeStringSizeNoTag(value: String): Int {
    // java's implementation uses a custom method for some optimizations.
    return computeLengthDelimitedFieldSize(value.length)
}

internal fun computeLengthDelimitedFieldSize(length: Int): Int = computeUInt32SizeNoTag(length) + length

//TODO: should this also be named "compute" for consistency?
internal fun getFixed64SizeNoTag(): Int = FIXED64_SIZE
internal fun computeSInt64SizeNoTag(value: Long): Int = computeUInt64SizeNoTag(encodeZigZag64(value))
internal fun computeInt64SizeNoTag(value: Long): Int = computeUInt64SizeNoTag(value)
//internal fun computeUInt64SizeNoTag(value: Long): Int = varintLength(value)

//TODO: should this also be named "compute" for consistency?
internal fun getFixed32SizeNoTag() = FIXED32_SIZE
internal fun computeSInt32SizeNoTag(value: Int) = computeUInt32SizeNoTag((encodeZigZag32(value)))
internal fun computeInt32SizeNoTag(value: Int) =
    if (value >= 0) computeUInt32SizeNoTag(value) else MAX_VARINT_SIZE

/** Compute the number of bytes that would be needed to encode an uint32 field. */
//TODO: use varintLen?
private fun computeUInt32SizeNoTag(value: Int): Int = when {
    value and (0.inv() shl 7) == 0 -> 1
    value and (0.inv() shl 14) == 0 -> 2
    value and (0.inv() shl 21) == 0 -> 3
    value and (0.inv() shl 28) == 0 -> 4
    else -> 5 // max varint32 size
}

private fun computeUInt64SizeNoTag(value: Long): Int {
    var _value = value
    // handle first two most common cases
    if ((_value and (0L.inv() shl 7)) == 0L) {
        return 1
    }
    if (_value < 0L) {
        return 10
    }
    // rest cases
    var size = 2
    if ((_value and (0L.inv() shl 35)) != 0L) {
        size += 4
        _value = value ushr 28
    }
    if ((_value and (0L.inv() shl 21)) != 0L) {
        size += 2
        _value = value ushr 14
    }
    if ((_value and (0L.inv() shl 14)) != 0L) {
        size += 1
    }
    return size
}

// helpers

// per protobuf spec 1-10 bytes
internal const val MAX_VARINT_SIZE = 10

// after decoding the varint representing a field, the low 3 bits tell us the wire type,
// and the rest of the integer tells us the field number.
private const val TAG_TYPE_BITS = 3

/**
 * See [Scalar type values](https://developers.google.com/protocol-buffers/docs/proto#scalar).
 */

private const val FIXED32_SIZE = 4
private const val FIXED64_SIZE = 8

internal fun computeTagSize(protoId: Int): Int = computeUInt32SizeNoTag(makeTag(protoId, 0))
private fun makeTag(protoId: Int, wireType: Int): Int = protoId shl TAG_TYPE_BITS or wireType

private fun varintLength(value: Long): Int = VAR_INT_LENGTHS[value.countLeadingZeroBits()]

/*
 * Map number of leading zeroes -> varint size
 * See the explanation in this blogpost: https://richardstartin.github.io/posts/dont-use-protobuf-for-telemetry
 */
//TODO: align with ProtobufWriter companion.object
private val VAR_INT_LENGTHS = IntArray(65) {
    (63 - it) / 7
}

// stream utils

internal fun encodeZigZag64(value: Long): Long = (value shl 1) xor (value shr 63)

internal fun encodeZigZag32(value: Int): Int = ((value shl 1) xor (value shr 31))