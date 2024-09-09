package io.embrace.android.embracesdk.internal.serialization

import io.embrace.android.embracesdk.internal.logging.EmbLogger
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Type

internal class DecoratedSerializer(
    private val impl: PlatformSerializer,
    private val logger: EmbLogger
) : PlatformSerializer {

    override fun <T> toJson(src: T): String {
        return serializerAction { impl.toJson(src) }
    }

    override fun <T> toJson(src: T, clz: Class<T>): String {
        return serializerAction { impl.toJson(src, clz) }
    }

    override fun <T> toJson(src: T, type: Type): String {
        return serializerAction { impl.toJson(src, type) }
    }

    override fun <T> toJson(any: T, clazz: Class<T>, outputStream: OutputStream) {
        return serializerAction { impl.toJson(any, clazz, outputStream) }
    }

    override fun <T> toJson(any: T, type: Type, outputStream: OutputStream) {
        return serializerAction { impl.toJson(any, type, outputStream) }
    }

    override fun <T> fromJson(json: String, clz: Class<T>): T {
        return serializerAction { impl.fromJson(json, clz) }
    }

    override fun <T> fromJson(json: String, type: Type): T {
        return serializerAction { impl.fromJson(json, type) }
    }

    override fun <T> fromJson(inputStream: InputStream, clz: Class<T>): T {
        return serializerAction { impl.fromJson(inputStream, clz) }
    }

    override fun <T> fromJson(inputStream: InputStream, type: Type): T {
        return serializerAction { impl.fromJson(inputStream, type) }
    }

    private fun <T> serializerAction(action: () -> T): T {
        try {
            return action()
        } catch (exc: Exception) {
            logger.logError("JSON serializer failed", exc)
            throw exc
        }
    }
}
