package io.embrace.android.embracesdk.internal.api.delegate

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeExperimentTrackingService
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.createExperimentBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.capture.experiment.ExperimentKind
import io.embrace.android.embracesdk.internal.capture.experiment.TrackedData
import io.embrace.android.embracesdk.internal.config.behavior.ExperimentBehaviorImpl
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ExperimentApiDelegateTest {

    private lateinit var delegate: ExperimentApiDelegate
    private lateinit var fakeExperimentTrackingService: FakeExperimentTrackingService
    private lateinit var telemetryService: FakeTelemetryService
    private lateinit var initLogger: FakeInternalLogger
    private lateinit var checkerLogger: FakeInternalLogger
    private lateinit var sdkCallChecker: SdkCallChecker
    private lateinit var clock: FakeClock
    private lateinit var initModule: FakeInitModule

    @Before
    fun setUp() {
        fakeExperimentTrackingService = FakeExperimentTrackingService()
        telemetryService = FakeTelemetryService()
        initLogger = FakeInternalLogger()
        checkerLogger = FakeInternalLogger(throwOnInternalError = false)

        initModule = FakeInitModule(logger = initLogger)
        clock = checkNotNull(initModule.getFakeClock())
        sdkCallChecker = SdkCallChecker(checkerLogger, telemetryService)
        delegate = createDelegate(RemoteConfig())
    }

    @Test
    fun `trackExperiment before start buffers without recording usage or error`() {
        delegate.trackExperiment("exp1", startedAt = 1L)

        assertTrue(fakeExperimentTrackingService.trackedData.isEmpty())
        assertTrue(telemetryService.apiCalls.isEmpty())
        assertTrue(checkerLogger.sdkNotInitializedMessages.isEmpty())
    }

    @Test
    fun `untrackExperiment before start buffers without recording usage or error`() {
        delegate.untrackExperiment("exp1", endedAt = 1L)

        assertTrue(fakeExperimentTrackingService.untrackCalls.isEmpty())
        assertTrue(telemetryService.apiCalls.isEmpty())
        assertTrue(checkerLogger.sdkNotInitializedMessages.isEmpty())
    }

    @Test
    fun `trackFeatureFlag before start buffers without recording usage or error`() {
        delegate.trackFeatureFlag("flag1", startedAt = 1L)

        assertTrue(fakeExperimentTrackingService.trackedData.isEmpty())
        assertTrue(telemetryService.apiCalls.isEmpty())
        assertTrue(checkerLogger.sdkNotInitializedMessages.isEmpty())
    }

    @Test
    fun `untrackFeatureFlag before start buffers without recording usage or error`() {
        delegate.untrackFeatureFlag("flag1", endedAt = 1L)

        assertTrue(fakeExperimentTrackingService.untrackCalls.isEmpty())
        assertTrue(telemetryService.apiCalls.isEmpty())
        assertTrue(checkerLogger.sdkNotInitializedMessages.isEmpty())
    }

    @Test
    fun `buffered calls are coalesced with api usage record preserved when flushed`() {
        delegate.trackExperiments(
            listOf(
                TrackedExperimentImpl("exp1", "v1", 123456789L),
                TrackedExperimentImpl("exp2", "v1", 123456789L),
            ),
        )
        delegate.trackExperiments(
            listOf(
                TrackedExperimentImpl("exp2", "v2", 123456789L),
                TrackedExperimentImpl("exp3", "v1", 123456789L),
            ),
        )
        delegate.trackExperiment("exp4", "v2", 123456789L)
        delegate.trackFeatureFlags(
            listOf(
                TrackedFeatureFlagImpl("flag1", 987654321L),
                TrackedFeatureFlagImpl("flag2", 987654321L),
            ),
        )
        delegate.trackFeatureFlag("flag3", startedAt = 987654321L)
        delegate.untrackExperiments(listOf("exp1", "exp2"), 555555555L)
        delegate.untrackExperiment("exp3", 555555555L)
        delegate.untrackExperiment("exp4", 555555566L)
        delegate.untrackFeatureFlags(listOf("flag1", "flag3"), 555555555L)
        delegate.untrackFeatureFlag("flag1", endedAt = 666666666L)
        delegate.untrackFeatureFlag("flag2", endedAt = 666666666L)

        sdkCallChecker.started.set(true)
        delegate.flushPendingCalls()

        assertEquals(
            listOf(
                "track_experiment",
                "track_experiment",
                "track_experiment",
                "track_feature_flag",
                "track_feature_flag",
                "untrack_experiment",
                "untrack_experiment",
                "untrack_experiment",
                "untrack_feature_flag",
                "untrack_feature_flag",
                "untrack_feature_flag",
            ),
            telemetryService.apiCalls,
        )
        assertEquals(5, fakeExperimentTrackingService.serviceInvocations)
    }

    @Test
    fun `buffered calls with omitted timestamps capture the call time from the system clock at the time of the API call`() {
        val beforeMs = System.currentTimeMillis()
        delegate.trackExperiment("exp1")
        delegate.untrackFeatureFlag("flag1")
        val afterMs = System.currentTimeMillis()
        clock.tick()

        sdkCallChecker.started.set(true)
        val flushTime = clock.now()
        delegate.flushPendingCalls()

        val experiment = fakeExperimentTrackingService.trackedData.single() as TrackedData.Experiment
        assertTrue(experiment.startTimeMs in beforeMs..afterMs)
        assertTrue(experiment.startTimeMs != flushTime)
        val untrackCall = fakeExperimentTrackingService.untrackCalls.single()
        assertTrue(untrackCall.endTimeMs in beforeMs..afterMs)
        assertTrue(untrackCall.endTimeMs != flushTime)
    }

    @Test
    fun `buffer admits entries up to the absolute record limit, keeping the earliest`() {
        repeat(PENDING_ENTRY_LIMIT - 1) { i ->
            delegate.trackExperiment("exp-$i", startedAt = i.toLong())
        }
        // a bulk call straddling the limit keeps its earlier entries and drops the rest
        delegate.trackExperiments(
            listOf(
                delegate.createExperiment("exp-kept", startedAt = 1L),
                delegate.createExperiment("exp-dropped", startedAt = 2L),
            ),
        )
        delegate.trackExperiment("exp-after-full", startedAt = 3L)

        sdkCallChecker.started.set(true)
        delegate.flushPendingCalls()

        val flushedIds = fakeExperimentTrackingService.trackedData.map { it.id }
        assertEquals(PENDING_ENTRY_LIMIT, flushedIds.size)
        assertTrue(flushedIds.contains("exp-0"))
        assertTrue(flushedIds.contains("exp-kept"))
        assertFalse(flushedIds.contains("exp-dropped"))
        assertFalse(flushedIds.contains("exp-after-full"))
    }

    @Test
    fun `trackExperiment after SDK start calls into the internal service immediately`() {
        sdkCallChecker.started.set(true)

        delegate.trackExperiment("exp1", variant = "v1", startedAt = 111L)

        assertEquals(
            listOf<TrackedData>(TrackedData.Experiment(id = "exp1", startTimeMs = 111L, variant = "v1")),
            fakeExperimentTrackingService.trackedData,
        )
        assertEquals(listOf("track_experiment"), telemetryService.apiCalls)
    }

    @Test
    fun `untrackExperiments after SDK start calls into the internal service immediately`() {
        sdkCallChecker.started.set(true)

        delegate.untrackExperiments(listOf("exp1", "exp2"), endedAt = 222L)

        assertEquals(
            listOf(FakeExperimentTrackingService.UntrackCall(ExperimentKind.EXPERIMENT, listOf("exp1", "exp2"), 222L)),
            fakeExperimentTrackingService.untrackCalls,
        )
        assertEquals(listOf("untrack_experiment"), telemetryService.apiCalls)
    }

    @Test
    fun `trackFeatureFlag after SDK start calls into the internal service immediately`() {
        sdkCallChecker.started.set(true)

        delegate.trackFeatureFlag("flag1", startedAt = 333L)

        assertEquals(
            listOf<TrackedData>(TrackedData.FeatureFlag(id = "flag1", startTimeMs = 333L)),
            fakeExperimentTrackingService.trackedData,
        )
        assertEquals(listOf("track_feature_flag"), telemetryService.apiCalls)
    }

    @Test
    fun `untrackFeatureFlag after SDK start calls into the internal service immediately`() {
        sdkCallChecker.started.set(true)

        delegate.untrackFeatureFlag("flag1", endedAt = 444L)

        assertEquals(
            listOf(FakeExperimentTrackingService.UntrackCall(ExperimentKind.FEATURE_FLAG, listOf("flag1"), 444L)),
            fakeExperimentTrackingService.untrackCalls,
        )
        assertEquals(listOf("untrack_feature_flag"), telemetryService.apiCalls)
    }

    @Test
    fun `omitted timestamps after SDK start resolve to the clock time at the moment of the call`() {
        sdkCallChecker.started.set(true)

        val trackTimeMs = clock.now()
        delegate.trackFeatureFlag("flag1")
        val untrackTimeMs = clock.tick()
        delegate.untrackExperiment("exp1")

        assertEquals(
            listOf<TrackedData>(TrackedData.FeatureFlag(id = "flag1", startTimeMs = trackTimeMs)),
            fakeExperimentTrackingService.trackedData,
        )
        assertEquals(
            listOf(FakeExperimentTrackingService.UntrackCall(ExperimentKind.EXPERIMENT, listOf("exp1"), untrackTimeMs)),
            fakeExperimentTrackingService.untrackCalls,
        )
    }

    @Test
    fun `single-entry and bulk forms produce identical results`() {
        sdkCallChecker.started.set(true)

        delegate.trackExperiment("exp1", variant = "v1", startedAt = 111L)
        delegate.trackExperiments(listOf(delegate.createExperiment("exp1", variant = "v1", startedAt = 111L)))
        delegate.trackFeatureFlag("flag1", startedAt = 222L)
        delegate.trackFeatureFlags(listOf(delegate.createFeatureFlag("flag1", startedAt = 222L)))
        delegate.untrackExperiment("exp1", endedAt = 333L)
        delegate.untrackExperiments(listOf("exp1"), endedAt = 333L)

        val trackedData = fakeExperimentTrackingService.trackedData
        assertEquals(4, trackedData.size)
        assertEquals(trackedData[0], trackedData[1])
        assertEquals(trackedData[2], trackedData[3])
        val untrackCalls = fakeExperimentTrackingService.untrackCalls
        assertEquals(2, untrackCalls.size)
        assertEquals(untrackCalls[0], untrackCalls[1])
    }

    @Test
    fun `empty bulk calls do not throw and leave nothing tracked`() {
        delegate.trackExperiments(emptyList())
        delegate.untrackExperiments(emptyList(), endedAt = 0L)
        delegate.trackFeatureFlags(emptyList())
        delegate.untrackFeatureFlags(emptyList(), endedAt = 0L)

        sdkCallChecker.started.set(true)
        delegate.flushPendingCalls()

        delegate.trackExperiments(emptyList())
        delegate.untrackExperiments(emptyList(), endedAt = 0L)
        delegate.trackFeatureFlags(emptyList())
        delegate.untrackFeatureFlags(emptyList(), endedAt = 0L)

        assertTrue(fakeExperimentTrackingService.trackedData.isEmpty())
        assertTrue(fakeExperimentTrackingService.untrackCalls.all { it.ids.isEmpty() })
    }

    private fun createDelegate(remoteConfig: RemoteConfig): ExperimentApiDelegate {
        val moduleInitBootstrapper = ModuleInitBootstrapper(
            initModule,
            configServiceSupplier = { _, _, _, _, _ ->
                FakeConfigService(experimentBehavior = createExperimentBehavior(remoteConfig))
            },
            essentialServiceModuleSupplier = { _, _, _, _, _, _, _, _, _ ->
                FakeEssentialServiceModule(experimentTrackingService = fakeExperimentTrackingService)
            },
        )
        moduleInitBootstrapper.init(ApplicationProvider.getApplicationContext())
        return ExperimentApiDelegate(moduleInitBootstrapper, sdkCallChecker)
    }

    private companion object {
        private const val PENDING_ENTRY_LIMIT = ExperimentBehaviorImpl.MAX_EXPERIMENT_COUNT_LIMIT
    }
}
