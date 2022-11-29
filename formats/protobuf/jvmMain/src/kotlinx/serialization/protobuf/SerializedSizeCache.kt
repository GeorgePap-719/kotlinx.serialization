package kotlinx.serialization.protobuf

import kotlinx.serialization.descriptors.SerialDescriptor
import java.util.concurrent.ConcurrentHashMap

internal actual fun createSerializedSizeCache(): SerializedSizeCache {
    return ConcurrentHashMapSerializedCache()
}

private class ConcurrentHashMapSerializedCache : SerializedSizeCache {
    private val cache = ConcurrentHashMap<SerialDescriptor, Int>()

    override fun get(key: SerialDescriptor): Int? = cache[key]

    override fun put(key: SerialDescriptor, size: Int) {
        cache[key] = size
    }
}
