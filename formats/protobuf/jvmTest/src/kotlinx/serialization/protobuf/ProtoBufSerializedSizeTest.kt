package kotlinx.serialization.protobuf

import kotlinx.serialization.Serializable
import org.junit.Test

class ProtoBufSerializedSizeTest {

    private val protoBuf = ProtoBuf

    @Serializable
    data class Foo(
        val valueString1: String? = null,
        val valueString2: String? = null,
        val valueInt1: Int? = null,
        val valueInt2: Int? = null
    )

    // damn, it works!
    @Test
    fun shouldCalculateStringSize() {
        val foo = Foo("some value", "some value", 1, 2)
        val size = protoBuf.getOrComputeSerializedSize(Foo.serializer(), foo)
        println(size)
    }
}