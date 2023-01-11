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

    @Serializable
    data class DataOuterMessage(
        val a: Int,
        val d: Double,
        @ProtoNumber(10)
        val inner: DataAllTypes,
        @ProtoNumber(20)
        val s: String
    )

    @Test
    fun shouldCalculateOuterMessage() {
        val dataInner = DataAllTypes(
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
        val data = DataOuterMessage(10, 20.0, dataInner, "hi")
        val size = protoBuf.getOrComputeSerializedSize(DataOuterMessage.serializer(), data)
        val javaInner = TestAllTypes.newBuilder().apply {
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
        val javaOuter = TestOuterMessage.newBuilder().apply {
            a = 10
            b = 20.0
            inner = javaInner
            s = "hi"
        }.build()
        assertEquals(javaOuter.serializedSize, size)
    }

    @Serializable
    data class DataRepeatedIntMessage(
        val s: Int,
        @ProtoNumber(10)
        val b: List<Int>
    )

    @Test
    fun shouldCalculateRepeatedIntMessage() {
        val data = DataRepeatedIntMessage(1, listOf(10, 20, 10, 10, 10, 10))
        val size = protoBuf.getOrComputeSerializedSize(DataRepeatedIntMessage.serializer(), data)
        val javaType = TestRepeatedIntMessage.newBuilder().apply {
            s = 1
            addAllB(listOf(10, 20, 10, 10, 10, 10))
        }.build()
        assertEquals(javaType.serializedSize, size)
        println("java:${javaType.serializedSize} kotlin:$size")
    }

    @Serializable
    data class DataRepeatedObjectMessage(val inner: List<DataAllTypes>)

    @Test
    fun shouldCalculateRepeatedObjectMessage() {
        val dataInner = DataAllTypes(
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
        val data = DataRepeatedObjectMessage(listOf(dataInner))
        val size = protoBuf.getOrComputeSerializedSize(DataRepeatedObjectMessage.serializer(), data)
        val javaInner = TestAllTypes.newBuilder().apply {
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
        val javaType = TestRepeatedObjectMessage.newBuilder().apply {
            addAllInner(listOf(javaInner))
//            addInner(javaInner)
//            addInner(javaInner)
        }.build()
        assertEquals(javaType.serializedSize, size)
    }
}
















