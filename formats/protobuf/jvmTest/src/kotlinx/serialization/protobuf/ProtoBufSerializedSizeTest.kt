package kotlinx.serialization.protobuf

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.internal.computeStringSizeNoTag
import org.junit.Test

class ProtoBufSerializedSizeTest {

    private val protoBuf = ProtoBuf

    @Serializable
    data class Foo(val value: String)

    @Test
    fun shouldCalculateStringSize() {
        val foo = Foo("some value")
        val size = protoBuf.getOrComputeSerializedSize(Foo.serializer(), foo)
        println(size)
        val sizeNoTag = computeStringSizeNoTag(foo.value)
        println(sizeNoTag)
    }
}