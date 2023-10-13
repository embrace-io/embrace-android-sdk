package io.embrace.android.embracesdk.internal

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonIOException
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import io.embrace.android.embracesdk.comms.api.EmbraceUrl
import io.embrace.android.embracesdk.comms.api.EmbraceUrlAdapter
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import java.io.BufferedWriter
import java.lang.reflect.Type
import java.nio.charset.Charset

/**
 * A wrapper around Gson to allow for thread-safe serialization.
 */
internal class EmbraceSerializer {

    private val gson: ThreadLocal<Gson> = object : ThreadLocal<Gson>() {
        override fun initialValue(): Gson {
            return GsonBuilder()
                .registerTypeAdapter(EmbraceUrl::class.java, EmbraceUrlAdapter())
                .create()
        }
    }

    fun <T> toJson(src: T): String {
        return gson.get()?.toJson(src) ?: throw JsonIOException("Failed converting object to JSON.")
    }

    fun <T> toJson(src: T, type: Type): String {
        return gson.get()?.toJson(src, type)
            ?: throw JsonIOException("Failed converting object to JSON.")
    }

    fun <T> fromJson(json: String, type: Type): T? {
        return gson.get()?.fromJson(json, type)
    }

    fun <T> fromJson(json: String, clz: Class<T>): T? {
        return gson.get()?.fromJson(json, clz)
    }

    fun <T> writeToFile(any: T, clazz: Class<T>, bw: BufferedWriter): Boolean {
        return try {
            gson.get()?.toJson(any, clazz, JsonWriter(bw))
            true
        } catch (e: Exception) {
            InternalStaticEmbraceLogger.logDebug("cannot write to bufferedWriter", e)
            false
        }
    }

    fun <T> loadObject(jsonReader: JsonReader, clazz: Class<T>): T? {
        return gson.get()?.fromJson(jsonReader, clazz)
    }

    fun <T> bytesFromPayload(payload: T, clazz: Class<T>): ByteArray? {
        val json: String? = gson.get()?.toJson(payload, clazz.genericSuperclass)
        return json?.toByteArray(Charset.forName("UTF-8"))
    }
}
