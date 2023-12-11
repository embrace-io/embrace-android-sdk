package io.embrace.android.embracesdk.internal

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import io.embrace.android.embracesdk.comms.api.EmbraceUrl
import io.embrace.android.embracesdk.comms.api.EmbraceUrlAdapter
import io.embrace.android.embracesdk.internal.utils.threadLocal
import java.io.InputStream
import java.io.OutputStream

/**
 * A wrapper around Gson to allow for thread-safe serialization.
 */
internal class EmbraceSerializer {

    private val impl by threadLocal {
        GsonBuilder()
            .registerTypeAdapter(EmbraceUrl::class.java, EmbraceUrlAdapter())
            .create()
    }

    fun <T> toJson(src: T): String {
        return impl.toJson(src)
    }

    fun <T> toJson(any: T, clazz: Class<T>, outputStream: OutputStream) {
        outputStream.writer().buffered().use {
            impl.toJson(any, clazz, JsonWriter(it))
        }
    }

    fun <T> fromJson(json: String, clz: Class<T>): T {
        return impl.fromJson(json, clz)
    }

    fun <T> fromJson(inputStream: InputStream, clz: Class<T>): T {
        inputStream.bufferedReader().use {
            return impl.fromJson(JsonReader(it), clz)
        }
    }

    fun <T> fromJsonWithTypeToken(json: String): T {
        return impl.fromJson(json, object : TypeToken<T>() {}.type)
    }
}
