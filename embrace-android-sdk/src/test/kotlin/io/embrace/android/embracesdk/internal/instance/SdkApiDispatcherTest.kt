package io.embrace.android.embracesdk.internal.instance

import android.app.Activity
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.PropertyScope
import io.embrace.android.embracesdk.fakes.FakeInternalTelemetryService
import io.embrace.android.embracesdk.internal.api.SdkApi
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import java.lang.reflect.Proxy

@RunWith(AndroidJUnit4::class)
internal class SdkApiDispatcherTest {

    private val calls = mutableListOf<String>()
    private lateinit var telemetryService: FakeInternalTelemetryService
    private lateinit var sdk: SdkApiDispatcher

    @Before
    fun setUp() {
        telemetryService = FakeInternalTelemetryService()
        sdk = SdkApiDispatcher(recordingSdkApi(calls), telemetryService)
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
}

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
