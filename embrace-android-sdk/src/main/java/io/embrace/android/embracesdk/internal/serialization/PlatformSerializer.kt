package io.embrace.android.embracesdk.internal.serialization

import io.embrace.android.embracesdk.internal.utils.truncate
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Type

/**
 * Interface for JSON serializer wrapper than can then be wrapped for testing purposes
 */
internal interface PlatformSerializer {
    fun <T> toJson(src: T): String
    fun <T> toJson(src: T, clz: Class<T>): String
    fun <T> toJson(src: T, type: Type): String
    fun <T> toJson(any: T, clazz: Class<T>, outputStream: OutputStream)
    fun <T> toJson(any: T, type: Type, outputStream: OutputStream)
    fun <T> fromJson(json: String, clz: Class<T>): T
    fun <T> fromJson(json: String, type: Type): T
    fun <T> fromJson(inputStream: InputStream, clz: Class<T>): T
    fun <T> fromJson(inputStream: InputStream, type: Type): T
}

/**
 * Return the first 200 elements of [elements] as a JSON-encoded string
 */
internal fun PlatformSerializer.truncatedStacktrace(
    elements: Array<StackTraceElement>
) = toJson(elements.truncate(), List::class.java)
