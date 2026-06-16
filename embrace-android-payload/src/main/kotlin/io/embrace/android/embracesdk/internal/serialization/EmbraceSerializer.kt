@file:OptIn(ExperimentalSerializationApi::class)

package io.embrace.android.embracesdk.internal.serialization

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Default [PlatformSerializer] backed by the shared [embraceJson] instance.
 */
class EmbraceSerializer : PlatformSerializer {

    override fun <T> toJson(value: T, serializer: SerializationStrategy<T>): String =
        embraceJson.encodeToString(serializer, value)

    override fun <T> toJson(value: T, serializer: SerializationStrategy<T>, outputStream: OutputStream) {
        // Close on completion so wrapping streams (e.g. GZIPOutputStream) flush their trailing
        // blocks; encodeToStream does not close the stream itself.
        outputStream.use { embraceJson.encodeToStream(serializer, value, it) }
    }

    override fun <T> fromJson(json: String, deserializer: DeserializationStrategy<T>): T =
        embraceJson.decodeFromString(deserializer, json)

    override fun <T> fromJson(inputStream: InputStream, deserializer: DeserializationStrategy<T>): T =
        inputStream.use { embraceJson.decodeFromStream(deserializer, it) }
}
