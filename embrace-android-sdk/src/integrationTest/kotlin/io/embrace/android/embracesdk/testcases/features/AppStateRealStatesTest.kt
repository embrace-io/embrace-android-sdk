package io.embrace.android.embracesdk.testcases.features

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import io.embrace.android.embracesdk.assertions.findSpanByName
import io.embrace.android.embracesdk.assertions.getLogs
import io.embrace.android.embracesdk.fakes.TestStateDataSource
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.NetworkState.Status
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.PowerState.PowerMode
import io.embrace.android.embracesdk.internal.capture.connectivity.ConnectivityStatus
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.internal.state.AppStateKeys
import io.embrace.android.embracesdk.internal.state.PackedStates
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowPowerManager

/**
 * The three states the SDK ships, network, power saving and the screen, on the app state data model.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
internal class AppStateRealStatesTest(otelSdkMode: OtelSdkMode) {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode) {
        EmbraceSetupInterface(
            workersToFake = listOf(
                Worker.Background.SessionPersistenceWorker,
                Worker.Background.PeriodicCacheWorker,
                Worker.Background.NonIoRegWorker,
                Worker.Background.IoRegWorker,
            ),
        ).apply {
            getFakedWorkerExecutor(Worker.Background.SessionPersistenceWorker).blockingMode = false
            getFakedWorkerExecutor(Worker.Background.NonIoRegWorker).blockingMode = false
            getFakedWorkerExecutor(Worker.Background.IoRegWorker).blockingMode = false
        }
    }

    private val appStateOn = RemoteConfig(
        pctStateCaptureEnabledV2 = 100f,
        pctAppStateEnabled = 100f,
        pctMultiFilePersistenceEnabled = 100f,
        backgroundActivityConfig = BackgroundActivityRemoteConfig(100f),
    )

    private lateinit var application: Application
    private lateinit var shadowPowerManager: ShadowPowerManager
    private lateinit var shadowMainLooper: ShadowLooper

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext() as Application
        shadowPowerManager = Shadows.shadowOf(application.getSystemService(Context.POWER_SERVICE) as PowerManager)
        shadowMainLooper = Shadows.shadowOf(Looper.getMainLooper())
    }

    @Test
    fun `network changes are listed in the change log in order and stamp the logs after them`() {
        val steps = listOf(
            ConnectivityStatus.Wifi(false) to Status.WIFI_CONNECTING,
            ConnectivityStatus.Wifi(true) to Status.WIFI,
            ConnectivityStatus.None to Status.NOT_REACHABLE,
            ConnectivityStatus.Wan(false) to Status.WAN_CONNECTING,
            ConnectivityStatus.Wan(true) to Status.WAN,
        )
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(enabledFeatures = FakeEnabledFeatureConfig(networkConnectivityCapture = true)),
            persistedRemoteConfig = appStateOn,
            testCaseAction = {
                recordSession {
                    steps.forEachIndexed { index, (connectivity, _) ->
                        clock.tick(100L)
                        simulateConnectivityChange(connectivity)
                        embrace.logInfo("network-$index")
                    }
                }
            },
            assertAction = {
                val envelope = getSingleSessionEnvelope()
                val changeLog = envelope.changeLog()
                assertNotNull(changeLog.attribute(AppStateKeys.INITIAL_VALUE_PREFIX + "network"))
                assertTrue(envelope.spanNames().none { it == "emb-state-network" })

                val events = changeLog.stateEvents("network")
                assertEquals(steps.map { "${it.second}" }, events.map { it.attribute(AppStateKeys.NEW_VALUE) })
                assertTrue(events.all { it.attribute(AppStateKeys.NEW_VALUE_TYPE) == null })
                val seqs = events.map { checkNotNull(it.attribute(AppStateKeys.SEQ)).toLong() }
                assertEquals(seqs.sorted(), seqs)
                assertEquals(seqs.distinct(), seqs)

                val logs = getSingleLogEnvelope().getLogs { it.body?.startsWith("network-") == true }.associateBy { it.body }
                steps.forEachIndexed { index, (_, status) ->
                    val attributes = checkNotNull(logs.getValue("network-$index").attributes)
                    assertEquals("$status", attributes.packedValue("network"))
                    assertEquals(seqs[index].toString(), attributes.findAttributeValue(AppStateKeys.SEQ))
                }
            },
        )
    }

    @Test
    fun `power saving is in the opening values from the start and its toggles are listed`() {
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(enabledFeatures = FakeEnabledFeatureConfig(powerSaveCapture = true)),
            persistedRemoteConfig = appStateOn,
            testCaseAction = {
                recordSession {
                    setPowerSaveMode(true)
                    clock.tick(1000L)
                    setPowerSaveMode(false)
                }
            },
            assertAction = {
                val envelope = getSingleSessionEnvelope()
                val changeLog = envelope.changeLog()
                val initial = changeLog.attribute(AppStateKeys.INITIAL_VALUE_PREFIX + "power")
                assertTrue("$initial", initial in setOf("${PowerMode.UNKNOWN}", "${PowerMode.NORMAL}", "${PowerMode.LOW}"))
                assertTrue(envelope.spanNames().none { it == "emb-state-power" })

                val events = changeLog.stateEvents("power")
                assertEquals(listOf("${PowerMode.LOW}", "${PowerMode.NORMAL}"), events.map { it.attribute(AppStateKeys.NEW_VALUE) })
                assertTrue(events.all { it.attribute(AppStateKeys.NEW_VALUE_TYPE) == null })
            },
        )
    }

    @Test
    fun `screens are listed in the change log with generated values marked and stamp the logs emitted on them`() {
        val activities = listOf(
            Robolectric.buildActivity(HomeActivity::class.java),
            Robolectric.buildActivity(SettingsActivity::class.java),
        )
        testRule.runTest(
            persistedRemoteConfig = appStateOn.copy(pctNavigationStateCaptureEnabled = 100f),
            testCaseAction = {
                simulateOpeningActivities(
                    addStartupActivity = false,
                    startInBackground = true,
                    activitiesAndActions = activities.mapIndexed { index, activity ->
                        activity to { embrace.logInfo("screen-$index") }
                    },
                )
            },
            assertAction = {
                val envelope = getSessionEnvelopes(1).single()
                val changeLog = envelope.changeLog()
                val names = activities.map { it.get().localClassName }
                assertTrue(envelope.spanNames().none { it == "emb-state-screen-automatic" })

                // the part opens before any screen was seen, which is a value the SDK generated
                assertEquals("Initializing", changeLog.attribute(AppStateKeys.INITIAL_VALUE_PREFIX + "screen-automatic"))
                assertEquals("system", changeLog.attribute(AppStateKeys.INITIAL_VALUE_TYPE_PREFIX + "screen-automatic"))

                val events = changeLog.stateEvents("screen-automatic")
                assertEquals(names + "Backgrounded", events.map { it.attribute(AppStateKeys.NEW_VALUE) })
                assertEquals(listOf(null, null, "system"), events.map { it.attribute(AppStateKeys.NEW_VALUE_TYPE) })

                val logs = getSingleLogEnvelope().getLogs { it.body?.startsWith("screen-") == true }.associateBy { it.body }
                names.forEachIndexed { index, name ->
                    val attributes = checkNotNull(logs.getValue("screen-$index").attributes)
                    assertEquals(name, attributes.packedValue("screen-automatic"))
                    assertEquals(false, attributes.packedIsGenerated("screen-automatic"))
                }
            },
        )
    }

    @Test
    fun `changes of two states made together are numbered in the order they were made in one change log`() {
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(enabledFeatures = FakeEnabledFeatureConfig(networkConnectivityCapture = true)),
            persistedRemoteConfig = appStateOn,
            testCaseAction = {
                recordSession {
                    val test = findDataSource<TestStateDataSource>()
                    simulateConnectivityChange(ConnectivityStatus.Wifi(true))
                    test.onStateChange(newState = "one", transitionTimeMs = clock.now())
                    simulateConnectivityChange(ConnectivityStatus.Wan(true))
                    test.onStateChange(newState = "two", transitionTimeMs = clock.now())
                    // the part ends on a network that can deliver it
                    simulateConnectivityChange(ConnectivityStatus.Wifi(true))
                }
            },
            assertAction = {
                val changeLog = getSingleSessionEnvelope().changeLog()
                val initialSeq = checkNotNull(changeLog.attribute(AppStateKeys.INITIAL_SEQ)).toLong()
                val all = checkNotNull(changeLog.events)
                assertEquals(
                    (initialSeq + 1..initialSeq + all.size).toList(),
                    all.map { checkNotNull(it.attribute(AppStateKeys.SEQ)).toLong() },
                )

                val made = all.filter { it.name in setOf("emb-state-network", "emb-state-test") }
                assertEquals(
                    listOf(
                        "emb-state-network" to "${Status.WIFI}",
                        "emb-state-test" to "one",
                        "emb-state-network" to "${Status.WAN}",
                        "emb-state-test" to "two",
                        "emb-state-network" to "${Status.WIFI}",
                    ),
                    made.map { it.name to it.attribute(AppStateKeys.NEW_VALUE) },
                )
            },
        )
    }

    private fun setPowerSaveMode(powerSaveModeEnabled: Boolean) {
        shadowPowerManager.setIsPowerSaveMode(powerSaveModeEnabled)
        application.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        shadowMainLooper.idle()
    }

    private fun Envelope<SessionPartPayload>.changeLog(): Span = findSpanByName(AppStateKeys.CHANGE_LOG_SPAN_NAME)

    private fun Envelope<SessionPartPayload>.spanNames(): List<String?> = checkNotNull(data.spans).map { it.name }

    private fun Span.attribute(key: String): String? = attributes?.findAttributeValue(key)

    private fun SpanEvent.attribute(key: String): String? = attributes?.findAttributeValue(key)

    private fun Span.stateEvents(state: String): List<SpanEvent> =
        events.orEmpty().filter { it.name == AppStateKeys.CHANGE_LOG_EVENT_PREFIX + state }

    private fun List<Attribute>.packedValue(state: String): String? = packedState(state)?.value

    private fun List<Attribute>.packedIsGenerated(state: String): Boolean? = packedState(state)?.system

    private fun List<Attribute>.packedState(state: String) =
        PackedStates.unpack(checkNotNull(findAttributeValue(AppStateKeys.PACKED))).singleOrNull { it.name == state }

    class HomeActivity : Activity()
    class SettingsActivity : Activity()

    internal companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }
}
