package io.embrace.android.embracesdk.internal.instrumentation.crash.jvm

import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor

/**
 * Throws [error] for the first [failures] requests to serialize a list whose elements match [elementDescriptor], and
 * serializes everything else.
 */
internal class ThrowingPlatformSerializer(
    private val elementDescriptor: SerialDescriptor,
    private val error: Throwable,
    private var failures: Int = Int.MAX_VALUE,
    private val delegate: PlatformSerializer = TestPlatformSerializer(),
) : PlatformSerializer by delegate {

    override fun <T> toJson(value: T, serializer: SerializationStrategy<T>): String {
        val descriptor = serializer.descriptor
        if (failures > 0 && descriptor.elementsCount == 1 && descriptor.getElementDescriptor(0) == elementDescriptor) {
            failures--
            throw error
        }
        return delegate.toJson(value, serializer)
    }
}
