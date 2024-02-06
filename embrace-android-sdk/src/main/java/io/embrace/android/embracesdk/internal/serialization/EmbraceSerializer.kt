package io.embrace.android.embracesdk.internal.serialization

import com.squareup.moshi.Moshi
import io.embrace.android.embracesdk.internal.utils.threadLocal
import okio.buffer
import okio.sink
import okio.source
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Type

/**
 * A wrapper around the JSON library to allow for thread-safe serialization.
 */
internal class EmbraceSerializer {

    private val impl by threadLocal {
        Moshi.Builder()
            .add(EmbraceUrlAdapter())
            .build()
    }

    fun <T> toJson(src: T): String {
        val clz = checkNotNull(src)::class.java
        val adapter = impl.adapter<T>(clz)
        return adapter.toJson(src) ?: error("Failed converting object to JSON.")
    }

    fun <T> toJson(src: T, clz: Class<T>): String {
        val adapter = impl.adapter(clz)
        return adapter.toJson(src) ?: error("Failed converting object to JSON.")
    }

    fun <T> toJson(src: T, type: Type): String {
        val adapter = impl.adapter<T>(type)
        return adapter.toJson(src) ?: error("Failed converting object to JSON.")
    }

    fun <T> toJson(any: T, clazz: Class<T>, outputStream: OutputStream) {
        outputStream.sink().buffer().use {
            val adapter = impl.adapter(clazz)
            adapter.toJson(it, any)
        }
    }

    fun <T> fromJson(json: String, clz: Class<T>): T {
        val adapter = impl.adapter(clz)
        return adapter.fromJson(json) ?: error("JSON conversion failed.")
    }

    fun <T> fromJson(json: String, type: Type): T {
        val adapter = impl.adapter<T>(type)
        return adapter.fromJson(json) ?: error("JSON conversion failed.")
    }

    fun <T> fromJson(inputStream: InputStream, clz: Class<T>): T {
        return inputStream.source().buffer().use {
            val adapter = impl.adapter(clz)
            adapter.fromJson(it) ?: error("JSON conversion failed.")
        }
    }

    fun <T> fromJson(inputStream: InputStream, type: Type): T {
        return inputStream.source().buffer().use {
            val adapter = impl.adapter<T>(type)
            adapter.fromJson(it) ?: error("JSON conversion failed.")
        }
    }

    inline fun <reified T> fromJsonWithTypeToken(json: String): T {
        val adapter = impl.adapter(T::class.java)
        return adapter.fromJson(json) ?: error("JSON conversion failed.")
    }
}
