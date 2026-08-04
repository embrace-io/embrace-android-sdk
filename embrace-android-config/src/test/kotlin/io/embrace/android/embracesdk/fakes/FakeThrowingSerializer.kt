package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import kotlinx.serialization.DeserializationStrategy
import java.io.InputStream

/**
 * Fails every stream deserialization with [error], delegating everything else to a real serializer.
 *
 * [TestPlatformSerializer.errorOnNextOperation] only throws an [IllegalAccessException], so it cannot
 * exercise a handler that has to cope with a [Throwable] that is not an [Exception].
 */
internal class FakeThrowingSerializer(
    private val error: Throwable,
    private val impl: PlatformSerializer = EmbraceSerializer(),
) : PlatformSerializer by impl {

    override fun <T> fromJson(inputStream: InputStream, deserializer: DeserializationStrategy<T>): T = throw error
}
