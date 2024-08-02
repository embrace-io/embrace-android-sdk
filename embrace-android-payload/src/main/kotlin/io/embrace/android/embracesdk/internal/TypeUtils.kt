package io.embrace.android.embracesdk.internal

import com.squareup.moshi.Types
import java.lang.reflect.ParameterizedType
import kotlin.reflect.KClass

public object TypeUtils {

    public fun typedList(clz: KClass<*>): ParameterizedType =
        Types.newParameterizedType(List::class.java, clz.java)
}
