package io.embrace.android.embracesdk.internal.instrumentation.bytecode

import androidx.annotation.Keep
import io.embrace.android.embracesdk.internal.EmbraceInternalApi
import io.embrace.android.embracesdk.internal.instrumentation.okhttp.EmbraceOkHttpInterceptor
import io.embrace.android.embracesdk.internal.instrumentation.okhttp.InterceptorType
import io.embrace.android.embracesdk.internal.instrumentation.okhttp.OkHttpDataSource
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * @hide
 */
@Keep
object OkHttpBytecodeEntrypoint {

    /**
     * As there was a way to clear the injected interceptors during the OkHttpClient
     * initialization using the builder, we are hooking the build method as well, instead of
     * just the Builder constructor.
     *
     * Once the build method is called, OkHTTP mushes everything and returns the OkHttpClient
     * instance, where the developer has no way to alter any of the interceptors during or
     * after this point, without having to rebuild the client.
     */
    @Keep
    @JvmStatic
    @Suppress("unused")
    fun build(thiz: OkHttpClient.Builder) {
        addEmbraceInterceptors(thiz)
    }

    /**
     * Adds embrace interceptors if they don't exist already to the OkHTTPClient provided.
     *
     * @param thiz the OkHttpClient builder in matter.
     */
    private fun addEmbraceInterceptors(thiz: OkHttpClient.Builder) {
        try {
            val dataSource = OkHttpDataSource()
            addInterceptor(
                thiz.interceptors(),
                EmbraceOkHttpInterceptor(InterceptorType.APPLICATION, dataSource)
            )
            addInterceptor(
                thiz.networkInterceptors(),
                EmbraceOkHttpInterceptor(InterceptorType.NETWORK, dataSource)
            )
        } catch (error: Throwable) {
            EmbraceInternalApi.internalInterface.logInternalError(error)
        }
    }

    /**
     * Adds the interceptor to the interceptors list if it doesn't exist already.
     */
    private fun addInterceptor(
        interceptors: MutableList<Interceptor>?,
        interceptor: Interceptor,
    ) {
        if (interceptors != null && !interceptors.any { interceptor.javaClass.isInstance(it) }) {
            interceptors.add(0, interceptor)
        }
    }
}
