package io.embrace.android.gradle.plugin.util.serialization

import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Type

interface EmbraceSerializer {
    fun <T> toJson(data: T): String
    fun <T> toJson(data: T, type: Type): String
    fun <T> toJson(any: T, clazz: Class<T>, outputStream: OutputStream)
    fun <T> fromJson(json: String, type: Class<T>): T
    fun <T> fromJson(inputStream: InputStream, clz: Class<T>): T
}
