package io.embrace.android.embracesdk.okhttp3

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jetbrains.annotations.TestOnly
import java.io.IOException

/**
 * [Interceptor] used for testing that allows you to inspect and modify the request and responses that pass through this chain
 */
internal class TestInspectionInterceptor(
    private val beforeRequestSent: (Request) -> Request,
    private val afterResponseReceived: (Response) -> Response,
) : Interceptor {

    @TestOnly
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        return afterResponseReceived.invoke(chain.proceed(beforeRequestSent.invoke(chain.request())))
    }
}
