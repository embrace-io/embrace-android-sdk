@file:OptIn(ExperimentalSerializationApi::class)

package io.embrace.android.gradle.plugin.util.serialization

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.InputStream
import java.io.OutputStream

class JsonSerializer : EmbraceSerializer {

    override fun <T> toJson(data: T, serializer: SerializationStrategy<T>): String = wrapFailure("serialize") {
        pluginJson.encodeToString(serializer, data)
    }

    override fun <T> toJson(data: T, serializer: SerializationStrategy<T>, outputStream: OutputStream) =
        wrapFailure("serialize") {
            outputStream.buffered().use { pluginJson.encodeToStream(serializer, data, it) }
        }

    override fun <T> fromJson(json: String, deserializer: DeserializationStrategy<T>): T = wrapFailure("deserialize") {
        pluginJson.decodeFromString(deserializer, json)
    }

    override fun <T> fromJson(inputStream: InputStream, deserializer: DeserializationStrategy<T>): T =
        wrapFailure("deserialize") {
            inputStream.buffered().use { pluginJson.decodeFromStream(deserializer, it) }
        }

    private inline fun <R> wrapFailure(action: String, block: () -> R): R = try {
        block()
    } catch (e: Exception) {
        throw IllegalArgumentException("Failed to $action object: ${e.message}", e)
    }
}
