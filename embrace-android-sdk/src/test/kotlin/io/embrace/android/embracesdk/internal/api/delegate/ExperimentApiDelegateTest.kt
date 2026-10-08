package io.embrace.android.embracesdk.internal.api.delegate

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeExperimentTrackingService
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.capture.experiment.ExperimentApiCall
import io.embrace.android.embracesdk.internal.capture.experiment.ExperimentKind
import io.embrace.android.embracesdk.internal.capture.experiment.TrackedData
import io.embrace.android.embracesdk.internal.config.resolved.ExperimentConfig
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.instance.BufferingSdkInstance
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
    private lateinit var initLogger: FakeInternalLogger
    private lateinit var checkerLogger: FakeInternalLogger
    private lateinit var sdkCallChecker: SdkCallChecker
    private lateinit var clock: FakeClock
    private lateinit var initModule: FakeInitModule
    private lateinit var bufferLogger: FakeInternalLogger
    private lateinit var buffer: BufferingSdkInstance

    @Before
    fun setUp() {
        fakeExperimentTrackingService = FakeExperimentTrackingService()
        initLogger = FakeInternalLogger()
        checkerLogger = FakeInternalLogger(throwOnInternalError = false)

        initModule = FakeInitModule(logger = initLogger, otelSdkMode = OtelSdkMode.COMPAT)
        clock = checkNotNull(initModule.getFakeClock())
        sdkCallChecker = SdkCallChecker(checkerLogger)
        delegate = createDelegate()
        bufferLogger = FakeInternalLogger()
        buffer = BufferingSdkInstance(clock, bufferLogger)
    }

    @Test
    fun `trackExperiment before start buffers without logging an error`() {
        buffer.trackExperiment("exp1", startedAt = 1L)

        assertTrue(fakeExperimentTrackingService.trackedData.isEmpty())
        assertNoErrorsLogged()
    }

    @Test
    fun `untrackExperiment before start buffers without logging an error`() {
        buffer.untrackExperiment("exp1", endedAt = 1L)

        assertTrue(fakeExperimentTrackingService.untrackCalls.isEmpty())
        assertNoErrorsLogged()
    }

    @Test
    fun `trackFeatureFlag before start buffers without logging an error`() {
        buffer.trackFeatureFlag("flag1", startedAt = 1L)

        assertTrue(fakeExperimentTrackingService.trackedData.isEmpty())
        assertNoErrorsLogged()
    }

    @Test
    fun `untrackFeatureFlag before start buffers without logging an error`() {
        buffer.untrackFeatureFlag("flag1", endedAt = 1L)

        assertTrue(fakeExperimentTrackingService.untrackCalls.isEmpty())
        assertNoErrorsLogged()
    }

    @Test
    fun `buffered calls are replayed in one service call when drained`() {
        buffer.trackExperiments(
            listOf(
                TrackedExperimentImpl("exp1", "v1", 123456789L),
                TrackedExperimentImpl("exp2", "v1", 123456789L),
            ),
        )
        buffer.trackExperiments(
            listOf(
                TrackedExperimentImpl("exp2", "v2", 123456789L),
                TrackedExperimentImpl("exp3", "v1", 123456789L),
            ),
        )
        buffer.trackExperiment("exp4", "v2", 123456789L)
        buffer.trackFeatureFlags(
            listOf(
                TrackedFeatureFlagImpl("flag1", "on", 987654321L),
                TrackedFeatureFlagImpl("flag2", null, 987654321L),
            ),
        )
        buffer.trackFeatureFlag("flag3", variant = "v3", startedAt = 987654321L)
        buffer.untrackExperiments(listOf("exp1", "exp2"), 555555555L)
        buffer.untrackExperiment("exp3", 555555555L)
        buffer.untrackExperiment("exp4", 555555566L)
        buffer.untrackFeatureFlags(listOf("flag1", "flag3"), 555555555L)
        buffer.untrackFeatureFlag("flag1", endedAt = 666666666L)
        buffer.untrackFeatureFlag("flag2", endedAt = 666666666L)

        startAndDrain()

        assertEquals(1, fakeExperimentTrackingService.serviceInvocations)
        assertEquals(
            listOf(
                ExperimentApiCall.Track(
                    listOf(
                        TrackedData.experiment("exp1", 123456789L, "v1"),
                        TrackedData.experiment("exp2", 123456789L, "v1"),
                    ),
                ),
                ExperimentApiCall.Track(
                    listOf(
                        TrackedData.experiment("exp2", 123456789L, "v2"),
                        TrackedData.experiment("exp3", 123456789L, "v1"),
                    ),
                ),
                ExperimentApiCall.Track(listOf(TrackedData.experiment("exp4", 123456789L, "v2"))),
                ExperimentApiCall.Track(
                    listOf(
                        TrackedData.featureFlag("flag1", 987654321L, "on"),
                        TrackedData.featureFlag("flag2", 987654321L, null),
                    ),
                ),
                ExperimentApiCall.Track(listOf(TrackedData.featureFlag("flag3", 987654321L, "v3"))),
                ExperimentApiCall.Untrack(ExperimentKind.EXPERIMENT, listOf("exp1", "exp2"), 555555555L),
                ExperimentApiCall.Untrack(ExperimentKind.EXPERIMENT, listOf("exp3"), 555555555L),
                ExperimentApiCall.Untrack(ExperimentKind.EXPERIMENT, listOf("exp4"), 555555566L),
                ExperimentApiCall.Untrack(ExperimentKind.FEATURE_FLAG, listOf("flag1", "flag3"), 555555555L),
                ExperimentApiCall.Untrack(ExperimentKind.FEATURE_FLAG, listOf("flag1"), 666666666L),
                ExperimentApiCall.Untrack(ExperimentKind.FEATURE_FLAG, listOf("flag2"), 666666666L),
            ),
            fakeExperimentTrackingService.bulkApiCalls,
        )
        assertNoErrorsLogged()
    }

    @Test
    fun `buffered calls with omitted timestamps capture the clock time at the time of the API call`() {
        val callTimeMs = clock.now()
        buffer.trackExperiment("exp1")
        buffer.untrackFeatureFlag("flag1")
        clock.tick()

        startAndDrain()

        assertEquals(callTimeMs, fakeExperimentTrackingService.trackedData.single().startTimeMs)
        assertEquals(callTimeMs, fakeExperimentTrackingService.untrackCalls.single().endTimeMs)
    }

    @Test
    fun `buffer admits entries up to the absolute record limit, keeping the earliest`() {
        repeat(PENDING_ENTRY_LIMIT - 1) { i ->
            buffer.trackExperiment("exp-$i", startedAt = i.toLong())
        }
        // a bulk call straddling the limit keeps its earlier entries and drops the rest
        buffer.trackExperiments(
            listOf(
                buffer.createExperiment("exp-kept", startedAt = 1L),
                buffer.createExperiment("exp-dropped", startedAt = 2L),
            ),
        )
        buffer.trackExperiment("exp-after-full", startedAt = 3L)

        startAndDrain()

        val flushedIds = fakeExperimentTrackingService.trackedData.map { it.id }
        assertEquals(PENDING_ENTRY_LIMIT, flushedIds.size)
        assertTrue(flushedIds.contains("exp-0"))
        assertTrue(flushedIds.contains("exp-kept"))
        assertFalse(flushedIds.contains("exp-dropped"))
        assertFalse(flushedIds.contains("exp-after-full"))
    }

    @Test
    fun `buffered empty bulk calls do not call the service when drained`() {
        buffer.trackExperiments(emptyList())
        buffer.untrackExperiments(emptyList(), endedAt = 0L)
        buffer.trackFeatureFlags(emptyList())
        buffer.untrackFeatureFlags(emptyList(), endedAt = 0L)
        startAndDrain()
        assertEquals(0, fakeExperimentTrackingService.serviceInvocations)
    }

    @Test
    fun `calls after draining go straight to the service`() {
        startAndDrain()

        buffer.trackExperiment("exp1", variant = "v1", startedAt = 111L)
        buffer.untrackFeatureFlag("flag1", endedAt = 222L)

        assertEquals(
            listOf(TrackedData.experiment(id = "exp1", startTimeMs = 111L, variant = "v1")),
            fakeExperimentTrackingService.trackedData,
        )
        assertEquals(
            listOf(FakeExperimentTrackingService.UntrackCall(ExperimentKind.FEATURE_FLAG, listOf("flag1"), 222L)),
            fakeExperimentTrackingService.untrackCalls,
        )
        assertTrue(fakeExperimentTrackingService.bulkApiCalls.isEmpty())
    }

    @Test
    fun `trackExperiment after SDK start calls into the internal service immediately`() {
        sdkCallChecker.started.set(true)

        delegate.trackExperiment("exp1", variant = "v1", startedAt = 111L)

        assertEquals(
            listOf(TrackedData.experiment(id = "exp1", startTimeMs = 111L, variant = "v1")),
            fakeExperimentTrackingService.trackedData,
        )
    }

    @Test
    fun `untrackExperiments after SDK start calls into the internal service immediately`() {
        sdkCallChecker.started.set(true)

        delegate.untrackExperiments(listOf("exp1", "exp2"), endedAt = 222L)

        assertEquals(
            listOf(FakeExperimentTrackingService.UntrackCall(ExperimentKind.EXPERIMENT, listOf("exp1", "exp2"), 222L)),
            fakeExperimentTrackingService.untrackCalls,
        )
    }

    @Test
    fun `trackFeatureFlag after SDK start calls into the internal service immediately`() {
        sdkCallChecker.started.set(true)

        delegate.trackFeatureFlag("flag1", variant = "on", startedAt = 333L)

        assertEquals(
            listOf(TrackedData.featureFlag(id = "flag1", startTimeMs = 333L, variant = "on")),
            fakeExperimentTrackingService.trackedData,
        )
    }

    @Test
    fun `untrackFeatureFlag after SDK start calls into the internal service immediately`() {
        sdkCallChecker.started.set(true)

        delegate.untrackFeatureFlag("flag1", endedAt = 444L)

        assertEquals(
            listOf(FakeExperimentTrackingService.UntrackCall(ExperimentKind.FEATURE_FLAG, listOf("flag1"), 444L)),
            fakeExperimentTrackingService.untrackCalls,
        )
    }

    @Test
    fun `omitted timestamps after SDK start resolve to the clock time at the moment of the call`() {
        sdkCallChecker.started.set(true)

        val trackTimeMs = clock.now()
        delegate.trackFeatureFlag("flag1")
        val untrackTimeMs = clock.tick()
        delegate.untrackExperiment("exp1")

        assertEquals(
            listOf(TrackedData.featureFlag(id = "flag1", startTimeMs = trackTimeMs, variant = null)),
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
        delegate.trackFeatureFlag("flag1", variant = "on", startedAt = 222L)
        delegate.trackFeatureFlags(listOf(delegate.createFeatureFlag("flag1", variant = "on", startedAt = 222L)))
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
    fun `calls before start are dropped with an error`() {
        delegate.trackExperiment("exp1", startedAt = 1L)
        delegate.untrackFeatureFlag("flag1", endedAt = 1L)
        delegate.replay(listOf(ExperimentApiCall.Track(listOf(TrackedData.experiment("exp2", 1L, null)))))
        assertEquals(0, fakeExperimentTrackingService.serviceInvocations)
        assertEquals(2, checkerLogger.sdkNotInitializedMessages.size)
    }

    @Test
    fun `replayed calls are committed in one service call`() {
        val calls = listOf(
            ExperimentApiCall.Track(listOf(TrackedData.experiment("exp1", 111L, "v1"))),
            ExperimentApiCall.Untrack(ExperimentKind.FEATURE_FLAG, listOf("flag1"), 222L),
        )
        sdkCallChecker.started.set(true)
        delegate.replay(calls)

        assertEquals(1, fakeExperimentTrackingService.serviceInvocations)
        assertEquals(calls, fakeExperimentTrackingService.bulkApiCalls)
    }

    @Test
    fun `replaying no calls does not call the service`() {
        sdkCallChecker.started.set(true)
        delegate.replay(emptyList())
        assertEquals(0, fakeExperimentTrackingService.serviceInvocations)
    }

    @Test
    fun `empty bulk calls do not throw and leave nothing tracked`() {
        sdkCallChecker.started.set(true)

        delegate.trackExperiments(emptyList())
        delegate.untrackExperiments(emptyList(), endedAt = 0L)
        delegate.trackFeatureFlags(emptyList())
        delegate.untrackFeatureFlags(emptyList(), endedAt = 0L)

        assertTrue(fakeExperimentTrackingService.trackedData.isEmpty())
        assertTrue(fakeExperimentTrackingService.untrackCalls.all { it.ids.isEmpty() })
    }

    private fun createDelegate(): ExperimentApiDelegate {
        val moduleInitBootstrapper = ModuleInitBootstrapper(
            initModule,
            configServiceSupplier = { _, _, _, _, _ -> FakeConfigService() },
            essentialServiceModuleSupplier = { _, _, _, _, _, _, _, _, _ ->
                FakeEssentialServiceModule(experimentTrackingService = fakeExperimentTrackingService)
            },
        )
        moduleInitBootstrapper.init(ApplicationProvider.getApplicationContext())
        return ExperimentApiDelegate(moduleInitBootstrapper, sdkCallChecker)
    }

    private fun startAndDrain() {
        sdkCallChecker.started.set(true)
        buffer.drainExperimentCalls(delegate, delegate::replay)
    }

    private fun assertNoErrorsLogged() {
        assertTrue(checkerLogger.sdkNotInitializedMessages.isEmpty())
        assertTrue(bufferLogger.infoMessages.isEmpty())
    }

    private companion object {
        private const val PENDING_ENTRY_LIMIT = ExperimentConfig.MAX_COUNT_LIMIT
    }
}
