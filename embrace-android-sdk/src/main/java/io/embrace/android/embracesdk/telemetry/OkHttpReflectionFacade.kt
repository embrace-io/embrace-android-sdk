package io.embrace.android.embracesdk.telemetry

internal class OkHttpReflectionFacade {
    fun hasOkHttp3(): Boolean =
        runCatching {
            Class.forName("okhttp3.OkHttpClient", false, javaClass.classLoader)
            true
        }.getOrDefault(false)

    fun getOkHttp3Version(): String =
        runCatching {
            val okhttpObject = Class.forName("okhttp3.OkHttp", false, javaClass.classLoader)
            okhttpObject.getField("VERSION").get(okhttpObject)?.toString()
        }.getOrDefault("") ?: ""
}
