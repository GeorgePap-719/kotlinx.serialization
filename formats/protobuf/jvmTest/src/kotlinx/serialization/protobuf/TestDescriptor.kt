package kotlinx.serialization.protobuf

import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import org.junit.Test

class TestDescriptor {

    @Serializable
    data class Message(val value: String)

    @Test
    fun shouldTestSome() {
        val message = Message("hi")

        for (descriptor in Message.serializer().descriptor.elementDescriptors) {
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
    }
}