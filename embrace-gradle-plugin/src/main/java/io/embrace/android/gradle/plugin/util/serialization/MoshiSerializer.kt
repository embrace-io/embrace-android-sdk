package io.embrace.android.gradle.plugin.util.serialization

import com.squareup.moshi.Moshi
import okio.buffer
import okio.sink
import okio.source
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Type

class MoshiSerializer : EmbraceSerializer {

    private val moshiRef = object : ThreadLocal<Moshi>() {
        override fun initialValue(): Moshi = Moshi.Builder()
            .build()
    }

    private val moshiImpl by lazy { checkNotNull(moshiRef.get()) }

    override fun <T> toJson(data: T): String {
        return try {
            val javaClass = checkNotNull(data)::class.java
            val adapter = moshiImpl.adapter<T>(javaClass)
            adapter.toJson(data)
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to serialize object: ${e.message}", e)
        }
    }

    override fun <T> toJson(data: T, type: Type): String {
        return try {
            val adapter = moshiImpl.adapter<T>(type)
            adapter.toJson(data)
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to serialize object: ${e.message}", e)
        }
    }

    override fun <T> toJson(any: T, clazz: Class<T>, outputStream: OutputStream) {
        return try {
            outputStream.sink().buffer().use {
                val adapter = moshiImpl.adapter(clazz)
                adapter.toJson(it, any)
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to serialize object: ${e.message}", e)
        }
    }

    override fun <T> fromJson(json: String, type: Class<T>): T {
        return try {
            val adapter = moshiImpl.adapter(type)
            adapter.fromJson(json) ?: throw IllegalArgumentException("Failed to deserialize object")
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to deserialize object: ${e.message}", e)
        }
    }

    override fun <T> fromJson(inputStream: InputStream, clz: Class<T>): T {
        return try {
            inputStream.source().buffer().use {
                val adapter = moshiImpl.adapter(clz)
                adapter.fromJson(it) ?: error("JSON conversion failed.")
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to deserialize object: ${e.message}", e)
        }
    }
}
