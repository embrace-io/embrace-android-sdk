package io.embrace.android.embracesdk.internal.instrumentation.okhttp

import okhttp3.Interceptor
import okhttp3.Response

internal class EmbraceOkHttpInterceptor(
    private val type: InterceptorType,
    private val okhttpDataSourceProvider: () -> OkHttpDataSource?,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        return okhttpDataSourceProvider()?.interceptRequest(chain, type) ?: chain.proceed(chain.request())
    }
}
