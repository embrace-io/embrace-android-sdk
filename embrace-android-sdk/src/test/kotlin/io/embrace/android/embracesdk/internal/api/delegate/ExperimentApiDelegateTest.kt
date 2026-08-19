package io.embrace.android.embracesdk.internal.api.delegate

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeExperimentTrackingService
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.capture.experiment.TrackedData
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

    @Before
    fun setUp() {
        fakeExperimentTrackingService = FakeExperimentTrackingService()
        telemetryService = FakeTelemetryService()
        initLogger = FakeInternalLogger()
        checkerLogger = FakeInternalLogger(throwOnInternalError = false)

        val moduleInitBootstrapper = ModuleInitBootstrapper(
            FakeInitModule(logger = initLogger),
            essentialServiceModuleSupplier = { _, _, _, _, _, _, _, _, _ ->
                FakeEssentialServiceModule(experimentTrackingService = fakeExperimentTrackingService)
            },
        )
        moduleInitBootstrapper.init(ApplicationProvider.getApplicationContext())

        sdkCallChecker = SdkCallChecker(checkerLogger, telemetryService)
        delegate = ExperimentApiDelegate(moduleInitBootstrapper, sdkCallChecker)
    }

    @Test
    fun `trackExperiment before start buffers without recording usage or error`() {
        delegate.trackExperiment(delegate.createExperiment("exp1", 1L))

        assertTrue(fakeExperimentTrackingService.trackedData.isEmpty())
        assertTrue(telemetryService.apiCalls.isEmpty())
        assertTrue(checkerLogger.sdkNotInitializedMessages.isEmpty())
    }

    @Test
    fun `untrackExperiment before start buffers without recording usage or error`() {
        delegate.untrackExperiment("exp1", endTimeMs = 1L)

        assertTrue(fakeExperimentTrackingService.untrackCalls.isEmpty())
        assertTrue(telemetryService.apiCalls.isEmpty())
        assertTrue(checkerLogger.sdkNotInitializedMessages.isEmpty())
    }

    @Test
    fun `trackFeatureFlag before start buffers without recording usage or error`() {
        delegate.trackFeatureFlag(delegate.createFeatureFlag("flag1", 1L))

        assertTrue(fakeExperimentTrackingService.trackedData.isEmpty())
        assertTrue(telemetryService.apiCalls.isEmpty())
        assertTrue(checkerLogger.sdkNotInitializedMessages.isEmpty())
    }

    @Test
    fun `untrackFeatureFlag before start buffers without recording usage or error`() {
        delegate.untrackFeatureFlag("flag1", endTimeMs = 1L)

        assertTrue(fakeExperimentTrackingService.untrackCalls.isEmpty())
        assertTrue(telemetryService.apiCalls.isEmpty())
        assertTrue(checkerLogger.sdkNotInitializedMessages.isEmpty())
    }

    @Test
    fun `buffered calls flush in FIFO order`() {
        delegate.trackExperiment(delegate.createExperiment("exp1", startTimeMs = 123456789L, variant = "v1"))
        delegate.trackFeatureFlag(delegate.createFeatureFlag("flag1", startTimeMs = 987654321L))
        delegate.untrackExperiment("exp1", endTimeMs = 555555555L)
        delegate.untrackFeatureFlag("flag1", endTimeMs = 666666666L)

        sdkCallChecker.started.set(true)
        delegate.flushPendingCalls()

        assertEquals(
            listOf("track_experiment", "track_feature_flag", "untrack_experiment", "untrack_feature_flag"),
            telemetryService.apiCalls,
        )
        assertEquals(
            listOf(
                TrackedData.Experiment(id = "exp1", startTimeMs = 123456789L, variant = "v1"),
                TrackedData.FeatureFlag(id = "flag1", startTimeMs = 987654321L),
            ),
            fakeExperimentTrackingService.trackedData,
        )
        assertEquals(
            listOf(
                FakeExperimentTrackingService.UntrackCall(listOf("exp1"), 555555555L),
                FakeExperimentTrackingService.UntrackCall(listOf("flag1"), 666666666L),
            ),
            fakeExperimentTrackingService.untrackCalls,
        )
    }

    @Test
    fun `oldest buffered call is dropped once the pending event limit is exceeded`() {
        repeat(PENDING_EVENT_LIMIT + 1) { i ->
            delegate.trackExperiment(delegate.createExperiment("exp-$i", i.toLong()))
        }

        sdkCallChecker.started.set(true)
        delegate.flushPendingCalls()

        val flushedIds = fakeExperimentTrackingService.trackedData.map { it.id }
        assertEquals(PENDING_EVENT_LIMIT, flushedIds.size)
        assertFalse(flushedIds.contains("exp-0"))
        assertTrue(flushedIds.contains("exp-1"))
        assertTrue(flushedIds.contains("exp-$PENDING_EVENT_LIMIT"))
    }

    @Test
    fun `trackExperiment after SDK start calls into the internal service immediately`() {
        sdkCallChecker.started.set(true)

        delegate.trackExperiment(delegate.createExperiment("exp1", startTimeMs = 111L, variant = "v1"))

        assertEquals(
            listOf(TrackedData.Experiment(id = "exp1", startTimeMs = 111L, variant = "v1")),
            fakeExperimentTrackingService.trackedData,
        )
        assertEquals(listOf("track_experiment"), telemetryService.apiCalls)
    }

    @Test
    fun `untrackExperiment after SDK start calls into the internal service immediately`() {
        sdkCallChecker.started.set(true)

        delegate.untrackExperiment("exp1", "exp2", endTimeMs = 222L)

        assertEquals(
            listOf(FakeExperimentTrackingService.UntrackCall(listOf("exp1", "exp2"), 222L)),
            fakeExperimentTrackingService.untrackCalls,
        )
        assertEquals(listOf("untrack_experiment"), telemetryService.apiCalls)
    }

    @Test
    fun `trackFeatureFlag after SDK start calls into the internal service immediately`() {
        sdkCallChecker.started.set(true)

        delegate.trackFeatureFlag(delegate.createFeatureFlag("flag1", startTimeMs = 333L))

        assertEquals(
            listOf(TrackedData.FeatureFlag(id = "flag1", startTimeMs = 333L)),
            fakeExperimentTrackingService.trackedData,
        )
        assertEquals(listOf("track_feature_flag"), telemetryService.apiCalls)
    }

    @Test
    fun `untrackFeatureFlag after SDK start calls into the internal service immediately`() {
        sdkCallChecker.started.set(true)

        delegate.untrackFeatureFlag("flag1", endTimeMs = 444L)

        assertEquals(
            listOf(FakeExperimentTrackingService.UntrackCall(listOf("flag1"), 444L)),
            fakeExperimentTrackingService.untrackCalls,
        )
        assertEquals(listOf("untrack_feature_flag"), telemetryService.apiCalls)
    }

    @Test
    fun `empty vararg calls do not throw and leave nothing tracked`() {
        delegate.trackExperiment()
        delegate.untrackExperiment(endTimeMs = 0L)
        delegate.trackFeatureFlag()
        delegate.untrackFeatureFlag(endTimeMs = 0L)

        sdkCallChecker.started.set(true)
        delegate.flushPendingCalls()

        delegate.trackExperiment()
        delegate.untrackExperiment(endTimeMs = 0L)
        delegate.trackFeatureFlag()
        delegate.untrackFeatureFlag(endTimeMs = 0L)

        assertTrue(fakeExperimentTrackingService.trackedData.isEmpty())
        assertTrue(fakeExperimentTrackingService.untrackCalls.all { it.ids.isEmpty() })
    }

    private companion object {
        private const val PENDING_EVENT_LIMIT = 5000
    }
}
