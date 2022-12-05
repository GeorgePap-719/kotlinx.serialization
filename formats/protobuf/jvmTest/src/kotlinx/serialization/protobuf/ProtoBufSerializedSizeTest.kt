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

    @Test
    fun shouldCalculateSignedLongSize() {
        val data = DataSignedLong(10)
        val size = protoBuf.getOrComputeSerializedSize(DataSignedLong.serializer(), data)
        val javaType = TestSignedLong.newBuilder().apply { a = 10 }.build()
        assertEquals(javaType.serializedSize, size)
    }

    @Serializable
    data class DataFixedInt(@ProtoType(ProtoIntegerType.FIXED) val value: Int)

    @Test
    fun shouldCalculateFixedInt() {
        val data = DataFixedInt(10)
        val size = protoBuf.getOrComputeSerializedSize(DataFixedInt.serializer(), data)
        val javaType = TestFixedInt.newBuilder().apply { a = 10 }.build()
        assertEquals(javaType.serializedSize, size)
    }

    @Serializable
    data class DataDouble(val value: Double)

    @Test
    fun shouldCalculateDouble() {
        val data = DataDouble(10.0)
        val size = protoBuf.getOrComputeSerializedSize(DataDouble.serializer(), data)
        val javaType = TestDouble.newBuilder().apply { a = 10.0 }.build()
        assertEquals(javaType.serializedSize, size)
    }

    @Serializable
    data class DataBoolean(val value: Boolean)

    @Test
    fun shouldCalculateBoolean() {
        val data = DataBoolean(true)
        val size = protoBuf.getOrComputeSerializedSize(DataBoolean.serializer(), data)
        val javaType = TestBoolean.newBuilder().apply { a = true }.build()
        assertEquals(javaType.serializedSize, size)
    }
}