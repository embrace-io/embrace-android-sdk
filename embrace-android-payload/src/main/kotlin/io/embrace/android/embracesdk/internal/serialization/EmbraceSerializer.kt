package io.embrace.android.embracesdk.internal.serialization

import com.squareup.moshi.Moshi
import okio.buffer
import okio.sink
import okio.source
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Type

/**
 * A wrapper around the JSON library to allow for thread-safe serialization.
 */
class EmbraceSerializer : PlatformSerializer {

    private val ref = object : ThreadLocal<Moshi>() {
        override fun initialValue(): Moshi = Moshi.Builder()
            .add(AppFrameworkAdapter())
            .build()
    }

    private val impl by lazy { checkNotNull(ref.get()) }

    override fun <T> toJson(src: T): String {
        val clz = checkNotNull(src)::class.java
        val adapter = impl.adapter<T>(clz)
        return adapter.toJson(src) ?: error("Failed converting object to JSON.")
    }

    override fun <T> toJson(src: T, clz: Class<T>): String {
        val adapter = impl.adapter(clz)
        return adapter.toJson(src) ?: error("Failed converting object to JSON.")
    }

    override fun <T> toJson(src: T, type: Type): String {
        val adapter = impl.adapter<T>(type)
        return adapter.toJson(src) ?: error("Failed converting object to JSON.")
    }

    override fun <T> toJson(any: T, clazz: Class<T>, outputStream: OutputStream) {
        outputStream.sink().buffer().use {
            val adapter = impl.adapter(clazz)
            adapter.toJson(it, any)
        }
    }

    override fun <T> toJson(any: T, type: Type, outputStream: OutputStream) {
        outputStream.sink().buffer().use {
            val adapter = impl.adapter<T>(type)
            adapter.toJson(it, any)
        }
    }

    override fun <T> fromJson(json: String, clz: Class<T>): T {
        val adapter = impl.adapter(clz)
        return adapter.fromJson(json) ?: error("JSON conversion failed.")
    }

    override fun <T> fromJson(json: String, type: Type): T {
        val adapter = impl.adapter<T>(type)
        return adapter.fromJson(json) ?: error("JSON conversion failed.")
    }

    override fun <T> fromJson(inputStream: InputStream, clz: Class<T>): T {
        return inputStream.source().buffer().use {
            val adapter = impl.adapter(clz)
            adapter.fromJson(it) ?: error("JSON conversion failed.")
        }
    }

    override fun <T> fromJson(inputStream: InputStream, type: Type): T {
        return inputStream.source().buffer().use {
            val adapter = impl.adapter<T>(type)
            adapter.fromJson(it) ?: error("JSON conversion failed.")
        }
    }
}
