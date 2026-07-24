package io.embrace.android.embracesdk.internal.network.http

import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.network.http.HttpRequestInfo
import io.embrace.android.embracesdk.network.http.HttpRequestInfoModifier
import io.embrace.android.embracesdk.network.http.MutableHttpRequestInfo
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Holds the [HttpRequestInfoModifier]s registered by the consumer and applies them to the HTTP
 * request info captured by network instrumentation before it is reported as telemetry.
 *
 * Registering the same modifier more than once has no additional effect.
 */
class HttpRequestInfoModifierChain(
    private val logger: InternalLogger,
) {
    private val modifiers = CopyOnWriteArrayList<HttpRequestInfoModifier>()

    // we only log a single user callback failure per process to avoid generating a flood of error reports for a faulty callback
    private val loggedFailure = AtomicBoolean(false)

    fun add(modifier: HttpRequestInfoModifier) {
        modifiers.addIfAbsent(modifier)
    }

    fun remove(modifier: HttpRequestInfoModifier) {
        modifiers.remove(modifier)
    }

    /**
     * Applies every registered modifier to [info] and returns it. The same instance is returned so
     * callers can read the possibly-modified values back off it.
     */
    fun apply(info: MutableHttpRequestInfo): HttpRequestInfo {
        modifiers.forEach { modifier ->
            try {
                modifier.modifyHttpRequestInfo(info)
            } catch (t: Exception) {
                if (loggedFailure.compareAndSet(false, true)) {
                    logger.trackInternalError(InternalErrorType.HttpRequestInfoModifierFail, t)
                }
            }
        }
        return info
    }
}
