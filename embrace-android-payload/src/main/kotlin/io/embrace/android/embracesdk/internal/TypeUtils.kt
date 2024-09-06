package io.embrace.android.embracesdk.internal

import com.squareup.moshi.Types
import java.lang.reflect.ParameterizedType
import kotlin.reflect.KClass

object TypeUtils {

    fun parameterizedType(clz: KClass<*>, another: KClass<*>): ParameterizedType =
        Types.newParameterizedType(clz.java, another.java)

    fun typedList(clz: KClass<*>): ParameterizedType =
        Types.newParameterizedType(List::class.java, clz.java)

    fun <K, V> typedMap(keyClz: Class<K>, valueClz: Class<V>): ParameterizedType =
        Types.newParameterizedType(Map::class.java, keyClz, valueClz)
}
