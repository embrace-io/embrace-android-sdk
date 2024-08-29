package io.embrace.android.embracesdk.internal.serialization

import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Type

/**
 * Interface for JSON serializer wrapper than can then be wrapped for testing purposes
 */
public interface PlatformSerializer {
    public fun <T> toJson(src: T): String
    public fun <T> toJson(src: T, clz: Class<T>): String
    public fun <T> toJson(src: T, type: Type): String
    public fun <T> toJson(any: T, clazz: Class<T>, outputStream: OutputStream)
    public fun <T> toJson(any: T, type: Type, outputStream: OutputStream)
    public fun <T> fromJson(json: String, clz: Class<T>): T
    public fun <T> fromJson(json: String, type: Type): T
    public fun <T> fromJson(inputStream: InputStream, clz: Class<T>): T
    public fun <T> fromJson(inputStream: InputStream, type: Type): T
}

/**
 * Return the first 200 elements of [elements] as a JSON-encoded string
 */
public fun PlatformSerializer.truncatedStacktrace(
    elements: Array<StackTraceElement>
): String = toJson(elements.take(200).map(StackTraceElement::toString).toList(), List::class.java)
