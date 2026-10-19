package io.embrace.android.embracesdk.benchmark.fakes

import io.embrace.android.embracesdk.PropertyScope
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.api.SdkApi
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import java.lang.reflect.Proxy

/**
 * An [SdkApi] that keeps what a scenario records, for a test to read back. It models the calls scenarios make, and
 * any other call fails, so that a scenario that starts making one brings it into the fake rather than having it
 * silently ignored.
 *
 * A span takes its times from [clock] when it is given none, as the SDK's take them from the device's clock.
 */
internal class FakeSdkApi(private val clock: () -> Long) : SdkApi by throwingSdkApi() {

    val breadcrumbs: MutableList<String> = mutableListOf()
    val networkRequests: MutableList<EmbraceNetworkRequest> = mutableListOf()
    val spans: MutableList<FakeEmbraceSpan> = mutableListOf()
    val logs: MutableList<Log> = mutableListOf()
    val userSessionProperties: MutableMap<String, String> = mutableMapOf()

    /**
     * How many pieces of telemetry were recorded: breadcrumbs, requests, spans and logs.
     */
    val telemetryCount: Int
        get() = breadcrumbs.size + networkRequests.size + spans.size + logs.size

    override fun addBreadcrumb(message: String) {
        breadcrumbs += message
    }

    override fun recordNetworkRequest(networkRequest: EmbraceNetworkRequest) {
        networkRequests += networkRequest
    }

    override fun startSpan(
        name: String,
        parent: EmbraceSpan?,
        startTimeMs: Long?,
        autoTerminationMode: AutoTerminationMode,
    ): EmbraceSpan = newSpan(name, parent, autoTerminationMode, startTimeMs)

    override fun <T> recordSpan(
        name: String,
        parent: EmbraceSpan?,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        autoTerminationMode: AutoTerminationMode,
        code: () -> T,
    ): T {
        check(events.isEmpty()) { "FakeSdkApi does not model the events passed to recordSpan" }
        val span = newSpan(name, parent, autoTerminationMode, startTimeMs = null)
        attributes.forEach(span::addAttribute)
        try {
            return code().also { span.stop() }
        } catch (exc: Throwable) {
            span.stop(ErrorCode.FAILURE)
            throw exc
        }
    }

    override fun addUserSessionProperty(key: String, value: String, scope: PropertyScope): Boolean {
        userSessionProperties[key] = value
        return true
    }

    override fun logInfo(message: String) {
        logs += Log(Severity.INFO, message)
    }

    override fun logWarning(message: String) {
        logs += Log(Severity.WARNING, message)
    }

    override fun logError(message: String) {
        logs += Log(Severity.ERROR, message)
    }

    override fun logException(throwable: Throwable, severity: Severity, properties: Map<String, Any>, message: String?) {
        logs += Log(severity, message ?: throwable.message.orEmpty(), properties, throwable)
    }

    data class Log(
        val severity: Severity,
        val message: String,
        val properties: Map<String, Any> = emptyMap(),
        val throwable: Throwable? = null,
    )

    private fun newSpan(
        name: String,
        parent: EmbraceSpan?,
        autoTerminationMode: AutoTerminationMode,
        startTimeMs: Long?,
    ): FakeEmbraceSpan = FakeEmbraceSpan(name, parent, autoTerminationMode, clock).also { span ->
        span.start(startTimeMs)
        spans += span
    }
}

private fun throwingSdkApi(): SdkApi = Proxy.newProxyInstance(
    SdkApi::class.java.classLoader,
    arrayOf(SdkApi::class.java),
) { _, method, _ ->
    throw IllegalStateException(method.name)
} as SdkApi
