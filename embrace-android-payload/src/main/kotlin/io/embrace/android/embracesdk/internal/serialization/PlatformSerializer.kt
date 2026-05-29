package io.embrace.android.embracesdk.internal.serialization

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.serializer
import java.io.InputStream
import java.io.OutputStream

/**
 * Interface for JSON serializer wrapper that can be wrapped for testing purposes.
 *
 * The core methods take an explicit [SerializationStrategy] / [DeserializationStrategy] so the
 * interface stays free of reified type parameters. For ergonomic call sites, use the reified
 * inline extensions ([toJson], [fromJson]) defined below — the kotlinx-serialization compiler
 * plugin resolves [serializer]`<T>()` at compile time, capturing the full parameterized type.
 */
interface PlatformSerializer {
    fun <T> toJson(value: T, serializer: SerializationStrategy<T>): String
    fun <T> toJson(value: T, serializer: SerializationStrategy<T>, outputStream: OutputStream)
    fun <T> fromJson(json: String, deserializer: DeserializationStrategy<T>): T
    fun <T> fromJson(inputStream: InputStream, deserializer: DeserializationStrategy<T>): T
}

inline fun <reified T> PlatformSerializer.toJson(value: T): String =
    toJson(value, serializer())

inline fun <reified T> PlatformSerializer.toJson(value: T, outputStream: OutputStream): Unit =
    toJson(value, serializer(), outputStream)

inline fun <reified T> PlatformSerializer.fromJson(json: String): T =
    fromJson(json, serializer<T>())

inline fun <reified T> PlatformSerializer.fromJson(inputStream: InputStream): T =
    fromJson(inputStream, serializer<T>())

/**
 * Return the first 200 elements of [elements] as a JSON-encoded string.
 */
fun PlatformSerializer.truncatedStacktrace(elements: Array<StackTraceElement>): String =
    toJson(elements.take(200).map(StackTraceElement::toString).toList())
