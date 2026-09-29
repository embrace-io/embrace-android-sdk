package io.embrace.android.gradle.plugin.util.serialization

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import java.io.InputStream
import java.io.OutputStream

interface EmbraceSerializer {
    fun <T> toJson(data: T, serializer: SerializationStrategy<T>): String
    fun <T> toJson(data: T, serializer: SerializationStrategy<T>, outputStream: OutputStream)
    fun <T> fromJson(json: String, deserializer: DeserializationStrategy<T>): T
    fun <T> fromJson(inputStream: InputStream, deserializer: DeserializationStrategy<T>): T
}
