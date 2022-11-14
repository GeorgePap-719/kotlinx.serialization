package kotlinx.serialization.protobuf.internal

/** Compute the number of bytes that would be needed to encode an uint32 field. */
internal fun computeUInt32SizeNoTag(value: Int): Int = when {
    value and (0.inv() shl 7) == 0 -> 1
    value and (0.inv() shl 14) == 0 -> 2
    value and (0.inv() shl 21) == 0 -> 3
    value and (0.inv() shl 28) == 0 -> 4
    else -> 5 // max varint32 size
}