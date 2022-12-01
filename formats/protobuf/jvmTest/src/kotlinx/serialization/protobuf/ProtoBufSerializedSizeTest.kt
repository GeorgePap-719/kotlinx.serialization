package kotlinx.serialization.protobuf

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.TestData.*
import org.junit.Test
import kotlin.test.assertEquals

class ProtoBufSerializedSizeTest {

    private val protoBuf = ProtoBuf

    @Serializable
    data class DataInt32(val valueInt: Int)

    @Test
    fun shouldCalculateInt32Size() {
        val dataInt32 = DataInt32(1)
        val size = protoBuf.getOrComputeSerializedSize(DataInt32.serializer(), dataInt32)
        val javaType = TestInt32.newBuilder().apply { a = 1 }.build()
        assertEquals(javaType.serializedSize, size)
    }

    @Serializable
    data class DataSignedInt(@ProtoType(ProtoIntegerType.SIGNED) val value: Int)

    @Test
    fun shouldCalculateSingedIntSize() {
        val data = DataSignedInt(10)
        val size = protoBuf.getOrComputeSerializedSize(DataSignedInt.serializer(), data)
        val javaType = TestSignedInt.newBuilder().apply { a = 10 }.build()
        assertEquals(javaType.serializedSize, size)
    }

    @Serializable
    data class DataSignedLong(@ProtoType(ProtoIntegerType.SIGNED) val value: Long)

    @Test // fails
    fun shouldCalculateSignedLongSize() {
        val data = DataSignedLong(10)
        val size = protoBuf.getOrComputeSerializedSize(DataSignedLong.serializer(), data)
        val javaType = TestSignedLong.newBuilder().apply { a = 10 }.build()
        assertEquals(javaType.serializedSize, size)
    }

}