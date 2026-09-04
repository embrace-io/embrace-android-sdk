package io.embrace.android.embracesdk.internal.serialization

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.io.InputStream
import java.io.OutputStream

/**
 * Interface for JSON serializer wrapper that can be wrapped for testing purposes.
 *
 * Every method takes an explicit [SerializationStrategy] / [DeserializationStrategy]. Do not
 * resolve serializers at runtime due to potential performance impact. Instead, use the
 * generated ones(e.g. MapSerializer), which don't need to be resolved when used.
 */
interface PlatformSerializer {
    fun <T> toJson(value: T, serializer: SerializationStrategy<T>): String
    fun <T> toJson(value: T, serializer: SerializationStrategy<T>, outputStream: OutputStream)
    fun <T> fromJson(json: String, deserializer: DeserializationStrategy<T>): T
    fun <T> fromJson(inputStream: InputStream, deserializer: DeserializationStrategy<T>): T
}

/**
 * Return the first 200 elements of [elements] as a JSON-encoded string.
 */
fun PlatformSerializer.truncatedStacktrace(elements: Array<StackTraceElement>): String =
    toJson(elements.take(200).map(StackTraceElement::toString).toList(), stringListSerializer)

val stringListSerializer: SerializationStrategy<List<String>> = ListSerializer(String.serializer())
