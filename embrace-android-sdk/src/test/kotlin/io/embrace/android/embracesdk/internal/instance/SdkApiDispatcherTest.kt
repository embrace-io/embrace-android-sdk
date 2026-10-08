package io.embrace.android.embracesdk.internal.instance

import android.app.Activity
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.PropertyScope
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeInternalTelemetryService
import io.embrace.android.embracesdk.internal.api.SdkApi
import io.embrace.android.embracesdk.internal.otel.spans.NoopEmbraceSdkSpan
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import java.lang.reflect.Proxy

@RunWith(AndroidJUnit4::class)
internal class SdkApiDispatcherTest {

    private val calls = mutableListOf<String>()
    private lateinit var telemetryService: FakeInternalTelemetryService
    private lateinit var logger: FakeInternalLogger
    private lateinit var sdk: SdkApiDispatcher

    @Before
    fun setUp() {
        telemetryService = FakeInternalTelemetryService()
        logger = FakeInternalLogger(throwOnInternalError = false)
        sdk = SdkApiDispatcher(recordingSdkApi(calls), telemetryService, logger)
    }

    @Test
    fun `calls reach the target`() {
        sdk.logInfo("message")
        sdk.setUserIdentifier("user")
        sdk.addUserSessionProperty("key", "value", PropertyScope.PERMANENT)
        assertEquals(listOf("logInfo/1", "setUserIdentifier/1", "addUserSessionProperty/3"), calls)
    }

    @Test
    fun `calls reach a replaced target`() {
        val replacementCalls = mutableListOf<String>()
        sdk.target = recordingSdkApi(replacementCalls)
        sdk.logInfo("message")
        assertEquals(emptyList<String>(), calls)
        assertEquals(listOf("logInfo/1"), replacementCalls)
    }

    @Test
    fun `members with default bodies are forwarded rather than expanded`() {
        val activity = Robolectric.buildActivity(Activity::class.java).get()
        sdk.trackExperiment("exp")
        sdk.untrackExperiment("exp")
        sdk.trackFeatureFlag("flag")
        sdk.untrackFeatureFlag("flag")
        sdk.addLoadTraceChildSpan(activity, "span", 1, 2)
        sdk.addStartupTraceChildSpan("span", 1, 2)
        assertEquals(
            listOf(
                "trackExperiment/3",
                "untrackExperiment/2",
                "trackFeatureFlag/3",
                "untrackFeatureFlag/2",
                "addLoadTraceChildSpan/4",
                "addStartupTraceChildSpan/3",
            ),
            calls,
        )
    }

    @Test
    fun `public api usage is recorded`() {
        sdk.logInfo("message")
        sdk.logException(RuntimeException())
        sdk.setUserIdentifier("user")
        sdk.endUserSession()
        sdk.trackExperiment("exp")
        sdk.trackExperiments(emptyList())
        sdk.untrackFeatureFlags(emptyList())
        sdk.deviceId
        sdk.applicationInitEnd()
        assertEquals(
            listOf(
                "log_message",
                "log_message",
                "set_user_identifier",
                "end_session",
                "track_experiment",
                "track_experiment",
                "untrack_feature_flag",
                "get_device_id",
                "application_init_end",
            ),
            telemetryService.apiCalls,
        )
    }

    @Test
    fun `untracked apis do not record usage`() {
        sdk.isStarted
        sdk.createSpan("span")
        sdk.getSpan("id")
        sdk.createExperiment("exp")
        sdk.applicationInitStart()
        sdk.disable()
        assertEquals(emptyList<String>(), telemetryService.apiCalls)
    }

    @Test
    fun `sdk exceptions are reported and a fallback is returned`() {
        sdk.target = throwingSdkApi()
        sdk.logInfo("message")
        assertFalse(sdk.startView("view"))
        assertSame(NoopEmbraceSdkSpan, sdk.startSpan("span"))
        assertEquals("", sdk.deviceId)
        assertEquals(4, logger.internalErrorMessages.size)
    }

    @Test
    fun `user code runs when recordSpan throws before invoking it`() {
        sdk.target = throwingSdkApi()
        assertEquals("result", sdk.recordSpan("span") { "result" })
        assertEquals(1, logger.internalErrorMessages.size)
    }

    @Test
    fun `user code does not run twice when recordSpan throws after invoking it`() {
        var invocations = 0
        sdk.target = RecordSpanSdkApi { code -> code().also { error("sdk failure") } }
        val result = sdk.recordSpan("span") {
            invocations++
            "result"
        }
        assertEquals("result", result)
        assertEquals(1, invocations)
        assertEquals("sdk failure", logger.internalErrorMessages.single().throwable?.message)
    }

    @Test
    fun `user code exceptions propagate from recordSpan`() {
        sdk.target = RecordSpanSdkApi { code -> code() }
        val exc = IllegalArgumentException("user failure")
        assertSame(exc, assertThrows(IllegalArgumentException::class.java) { sdk.recordSpan("span") { throw exc } })
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }

    private class RecordSpanSdkApi(
        private val impl: (() -> Any?) -> Any?,
    ) : SdkApi by throwingSdkApi() {
        @Suppress("UNCHECKED_CAST")
        override fun <T> recordSpan(
            name: String,
            parent: EmbraceSpan?,
            attributes: Map<String, String>,
            events: List<EmbraceSpanEvent>,
            autoTerminationMode: AutoTerminationMode,
            code: () -> T,
        ): T = impl(code) as T
    }
}

private fun throwingSdkApi(): SdkApi = Proxy.newProxyInstance(
    SdkApi::class.java.classLoader,
    arrayOf(SdkApi::class.java),
) { _, method, _ ->
    throw IllegalStateException(method.name)
} as SdkApi

/**
 * An [SdkApi] that records each call as `name/argumentCount`, so that overloads are told apart.
 */
internal fun recordingSdkApi(calls: MutableList<String>): SdkApi = Proxy.newProxyInstance(
    SdkApi::class.java.classLoader,
    arrayOf(SdkApi::class.java),
) { _, method, _ ->
    calls += "${method.name}/${method.parameterCount}"
    if (method.returnType == Boolean::class.javaPrimitiveType) false else null
} as SdkApi
