package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.concurrency.ExecutionCoordinator
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import java.io.InputStream
import java.io.OutputStream

internal class TestPlatformSerializer(
    private val realSerializer: PlatformSerializer = EmbraceSerializer(),
    private val operationWrapper: ExecutionCoordinator.OperationWrapper = ExecutionCoordinator.OperationWrapper()
) : PlatformSerializer by realSerializer, ExecutionCoordinator.ExecutionModifiers by operationWrapper {

    override fun <T> toJson(any: T, clazz: Class<T>, outputStream: OutputStream) {
        operationWrapper.wrapOperation { realSerializer.toJson(any, clazz, outputStream) }
    }

    override fun <T> fromJson(inputStream: InputStream, clz: Class<T>): T {
        return operationWrapper.wrapOperation { realSerializer.fromJson(inputStream, clz) }
    }
}
