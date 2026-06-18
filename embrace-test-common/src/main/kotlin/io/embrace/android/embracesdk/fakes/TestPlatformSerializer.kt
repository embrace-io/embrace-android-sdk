package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.concurrency.ExecutionCoordinator
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import java.io.InputStream
import java.io.OutputStream

class TestPlatformSerializer(
    private val realSerializer: PlatformSerializer = EmbraceSerializer(),
    private val operationWrapper: ExecutionCoordinator.OperationWrapper = ExecutionCoordinator.OperationWrapper(),
) : PlatformSerializer, ExecutionCoordinator.ExecutionModifiers by operationWrapper {

    override fun <T> toJson(value: T, serializer: SerializationStrategy<T>): String =
        operationWrapper.wrapOperation { realSerializer.toJson(value, serializer) }

    override fun <T> toJson(value: T, serializer: SerializationStrategy<T>, outputStream: OutputStream) {
        operationWrapper.wrapOperation { realSerializer.toJson(value, serializer, outputStream) }
    }

    override fun <T> fromJson(json: String, deserializer: DeserializationStrategy<T>): T =
        operationWrapper.wrapOperation { realSerializer.fromJson(json, deserializer) }

    override fun <T> fromJson(inputStream: InputStream, deserializer: DeserializationStrategy<T>): T =
        operationWrapper.wrapOperation { realSerializer.fromJson(inputStream, deserializer) }
}
