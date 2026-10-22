package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.utils.EmbTrace
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

private const val DEFAULT_CONNECTION_TIMEOUT_SECONDS = 10L
private const val DEFAULT_READ_TIMEOUT_SECONDS = 60L

/**
 * Returns the [OkHttpClient] used for all SDK HTTP requests. It is only constructed on first use.
 */
fun lazyOkHttpClient(): Lazy<OkHttpClient> = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    EmbTrace.trace("okhttp-client-init") {
        OkHttpClient()
            .newBuilder()
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .connectTimeout(DEFAULT_CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}

fun Lazy<OkHttpClient>.evictConnections() {
    if (isInitialized()) {
        value.connectionPool.evictAll()
    }
}
