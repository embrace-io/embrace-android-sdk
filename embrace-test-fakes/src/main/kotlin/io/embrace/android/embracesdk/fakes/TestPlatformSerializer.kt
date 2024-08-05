package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.concurrency.ExecutionCoordinator
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Type

public class TestPlatformSerializer(
    private val realSerializer: PlatformSerializer = EmbraceSerializer(),
    private val operationWrapper: ExecutionCoordinator.OperationWrapper = ExecutionCoordinator.OperationWrapper()
) : PlatformSerializer, ExecutionCoordinator.ExecutionModifiers by operationWrapper {

    override fun <T> toJson(any: T, clazz: Class<T>, outputStream: OutputStream) {
        operationWrapper.wrapOperation { realSerializer.toJson(any, clazz, outputStream) }
    }

    override fun <T> fromJson(inputStream: InputStream, clz: Class<T>): T {
        return operationWrapper.wrapOperation { realSerializer.fromJson(inputStream, clz) }
    }

    override fun <T> toJson(src: T): String {
        return operationWrapper.wrapOperation { realSerializer.toJson(src) }
    }

    override fun <T> toJson(src: T, clz: Class<T>): String {
        return operationWrapper.wrapOperation { realSerializer.toJson(src, clz) }
    }

    override fun <T> toJson(src: T, type: Type): String {
        return operationWrapper.wrapOperation { realSerializer.toJson(src, type) }
    }

    override fun <T> toJson(any: T, type: Type, outputStream: OutputStream) {
        return operationWrapper.wrapOperation { realSerializer.toJson(any, type, outputStream) }
    }

    override fun <T> fromJson(json: String, clz: Class<T>): T {
        return operationWrapper.wrapOperation { realSerializer.fromJson(json, clz) }
    }

    override fun <T> fromJson(json: String, type: Type): T {
        return operationWrapper.wrapOperation { realSerializer.fromJson(json, type) }
    }

    override fun <T> fromJson(inputStream: InputStream, type: Type): T {
        return operationWrapper.wrapOperation { realSerializer.fromJson(inputStream, type) }
    }
}
