package kotlinx.serialization.protobuf

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.protobuf.internal.ProtoDesc
import kotlin.jvm.JvmField

@OptIn(ExperimentalSerializationApi::class)
internal open class ObjectCalculator(
    protobuf: ProtoBuf,
    @JvmField protected val parentTag: ProtoDesc,
    descriptor: SerialDescriptor
) : ProtoBufSerializedSizeCalculator(protobuf, descriptor) {
    override fun endEncode(descriptor: SerialDescriptor) {
        //TODO: update here the memoized size
    }
}
