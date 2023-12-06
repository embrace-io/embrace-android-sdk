package io.embrace.android.embracesdk.internal.serialization

import com.squareup.moshi.Moshi
import io.embrace.android.embracesdk.internal.utils.threadLocal
import okio.buffer
import okio.sink
import okio.source
import java.io.InputStream
import java.io.OutputStream

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
        val adapter = impl.adapter<T>(checkNotNull(src)::class.java)
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

    fun <T> fromJson(inputStream: InputStream, clz: Class<T>): T {
        return inputStream.source().buffer().use {
            val adapter = impl.adapter(clz)
            adapter.fromJson(it) ?: error("JSON conversion failed.")
        }
    }

    inline fun <reified T> fromJsonWithTypeToken(json: String): T {
        val adapter = impl.adapter(T::class.java)
        return adapter.fromJson(json) ?: error("JSON conversion failed.")
    }
}
