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

    @Serializable
    data class DataAllTypes(
        val int32: Int,
        @ProtoType(ProtoIntegerType.SIGNED)
        val sint32: Int,
        @ProtoType(ProtoIntegerType.FIXED)
        val fixed32: Int,
        @ProtoNumber(10)
        val int64: Long,
        @ProtoType(ProtoIntegerType.SIGNED)
        @ProtoNumber(11)
        val sint64: Long,
        @ProtoType(ProtoIntegerType.FIXED)
        @ProtoNumber(12)
        val fixed64: Long,
        @ProtoNumber(21)
        val float: Float,
        @ProtoNumber(22)
        val double: Double,
        @ProtoNumber(41)
        val bool: Boolean,
        @ProtoNumber(51)
        val string: String
    )

    @Test
    fun shouldCalculateAllTypes() {
        val data = DataAllTypes(
            1,
            2,
            3,
            4,
            5,
            6,
            7.0F,
            8.0,
            true,
            "hi"
        )
        val size = protoBuf.getOrComputeSerializedSize(DataAllTypes.serializer(), data)
        val javaType = TestAllTypes.newBuilder().apply {
            i32 = 1
            si32 = 2
            f32 = 3
            i64 = 4
            si64 = 5
            f64 = 6
            f = 7.0F
            d = 8.0
            b = true
            s = "hi"
        }.build()
        assertEquals(javaType.serializedSize, size)
    }
}