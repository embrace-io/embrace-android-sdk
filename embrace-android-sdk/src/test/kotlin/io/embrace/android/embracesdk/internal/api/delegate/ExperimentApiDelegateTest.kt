package io.embrace.android.embracesdk.internal.api.delegate

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.experiments.ExperimentTrackingScope
import io.embrace.android.embracesdk.experiments.ExperimentUntrackingScope
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
import io.embrace.android.embracesdk.internal.capture.experiment.UntrackedData
import io.embrace.android.embracesdk.internal.config.behavior.ExperimentBehaviorImpl
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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
    fun `buffered calls flush in FIFO order`() {
        delegate.trackExperiment("exp1", variant = "v1", startedAt = 123456789L)
        delegate.trackFeatureFlag("flag1", startedAt = 987654321L)
        delegate.untrackExperiment("exp1", endedAt = 555555555L)
        delegate.untrackFeatureFlag("flag1", endedAt = 666666666L)

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
                listOf(UntrackedData(ExperimentKind.EXPERIMENT, "exp1", 555555555L)),
                listOf(UntrackedData(ExperimentKind.FEATURE_FLAG, "flag1", 666666666L)),
            ),
            fakeExperimentTrackingService.untrackCalls,
        )
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
        val untrackedEntry = fakeExperimentTrackingService.untrackedData.single()
        assertTrue(untrackedEntry.endTimeMs in beforeMs..afterMs)
        assertTrue(untrackedEntry.endTimeMs != flushTime)
    }

    @Test
    fun `buffer admits entries up to the absolute record limit, keeping the earliest`() {
        repeat(PENDING_ENTRY_LIMIT - 1) { i ->
            delegate.trackExperiment("exp-$i", startedAt = i.toLong())
        }
        // a block straddling the limit keeps its earlier entries and drops the rest
        delegate.trackExperiments {
            experiment("exp-kept", startedAt = 1L)
            experiment("exp-dropped", startedAt = 2L)
        }
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
    fun `a mixed block straddling the record limit keeps the earliest declared entries of either kind`() {
        repeat(PENDING_ENTRY_LIMIT - 1) { i ->
            delegate.trackExperiment("exp-$i", startedAt = i.toLong())
        }
        delegate.trackExperiments {
            featureFlag("flag-kept", startedAt = 1L)
            experiment("exp-dropped", startedAt = 2L)
        }

        sdkCallChecker.started.set(true)
        delegate.flushPendingCalls()

        val flushedIds = fakeExperimentTrackingService.trackedData.map { it.id }
        assertTrue(flushedIds.contains("flag-kept"))
        assertFalse(flushedIds.contains("exp-dropped"))
        assertEquals("track_feature_flag", telemetryService.apiCalls.last())
    }

    @Test
    fun `replay after SDK start passes every buffered entry to the store, which enforces the configured cap`() {
        val delegate = createDelegate(RemoteConfig(experimentMaxCount = 2))
        delegate.trackExperiment("exp-0", startedAt = 1L)
        delegate.trackExperiments {
            experiment("exp-1", startedAt = 2L)
            experiment("exp-2", startedAt = 3L)
        }
        delegate.untrackExperiment("exp-0", endedAt = 4L)

        sdkCallChecker.started.set(true)
        delegate.flushPendingCalls()

        // the delegate does not trim to the configured cap: the store enforces it and counts the overage
        assertEquals(listOf("exp-0", "exp-1", "exp-2"), fakeExperimentTrackingService.trackedData.map { it.id })
        assertEquals(
            listOf(listOf(UntrackedData(ExperimentKind.EXPERIMENT, "exp-0", 4L))),
            fakeExperimentTrackingService.untrackCalls,
        )
        assertEquals(listOf("track_experiment", "track_experiment", "untrack_experiment"), telemetryService.apiCalls)
    }

    @Test
    fun `a mixed block buffered before start replays as a single call that records both counters`() {
        delegate.trackExperiments {
            experiment("exp1", startedAt = 1L)
            featureFlag("flag1", startedAt = 2L)
        }

        sdkCallChecker.started.set(true)
        delegate.flushPendingCalls()

        assertEquals(
            listOf(
                listOf<TrackedData>(
                    TrackedData.Experiment(id = "exp1", startTimeMs = 1L, variant = null),
                    TrackedData.FeatureFlag(id = "flag1", startTimeMs = 2L),
                ),
            ),
            fakeExperimentTrackingService.trackCalls,
        )
        assertEquals(listOf("track_experiment", "track_feature_flag"), telemetryService.apiCalls)
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

        delegate.untrackExperiments {
            experiment("exp1", endedAt = 222L)
            experiment("exp2", endedAt = 222L)
        }

        assertEquals(
            listOf(
                listOf(
                    UntrackedData(ExperimentKind.EXPERIMENT, "exp1", 222L),
                    UntrackedData(ExperimentKind.EXPERIMENT, "exp2", 222L),
                ),
            ),
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
            listOf(listOf(UntrackedData(ExperimentKind.FEATURE_FLAG, "flag1", 444L))),
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
            listOf(listOf(UntrackedData(ExperimentKind.EXPERIMENT, "exp1", untrackTimeMs))),
            fakeExperimentTrackingService.untrackCalls,
        )
    }

    @Test
    fun `every omitted timestamp in a block resolves to the same time even if the clock ticks inside it`() {
        sdkCallChecker.started.set(true)
        val callTimeMs = clock.now()

        delegate.trackExperiments {
            experiment("exp1")
            clock.tick(1000L)
            featureFlag("flag1")
        }
        delegate.untrackExperiments {
            experiment("exp1")
            clock.tick(1000L)
            featureFlag("flag1")
        }

        assertEquals(listOf(callTimeMs, callTimeMs), fakeExperimentTrackingService.trackedData.map { it.startTimeMs })
        assertEquals(
            listOf(callTimeMs + 1000L, callTimeMs + 1000L),
            fakeExperimentTrackingService.untrackedData.map { it.endTimeMs },
        )
    }

    @Test
    fun `single-entry and DSL forms produce identical results`() {
        sdkCallChecker.started.set(true)

        delegate.trackExperiment("exp1", variant = "v1", startedAt = 111L)
        delegate.trackExperiments { experiment("exp1", variant = "v1", startedAt = 111L) }
        delegate.trackFeatureFlag("flag1", startedAt = 222L)
        delegate.trackExperiments { featureFlag("flag1", startedAt = 222L) }
        delegate.untrackExperiment("exp1", endedAt = 333L)
        delegate.untrackExperiments { experiment("exp1", endedAt = 333L) }

        val trackedData = fakeExperimentTrackingService.trackedData
        assertEquals(4, trackedData.size)
        assertEquals(trackedData[0], trackedData[1])
        assertEquals(trackedData[2], trackedData[3])
        val untrackCalls = fakeExperimentTrackingService.untrackCalls
        assertEquals(2, untrackCalls.size)
        assertEquals(untrackCalls[0], untrackCalls[1])
    }

    @Test
    fun `a mixed track block records both kinds in declaration order in one call`() {
        sdkCallChecker.started.set(true)

        delegate.trackExperiments {
            featureFlag("flag1", startedAt = 1L)
            experiment("exp1", variant = "v1", startedAt = 2L)
            featureFlag("flag2", startedAt = 3L)
        }

        assertEquals(
            listOf(
                listOf<TrackedData>(
                    TrackedData.FeatureFlag(id = "flag1", startTimeMs = 1L),
                    TrackedData.Experiment(id = "exp1", startTimeMs = 2L, variant = "v1"),
                    TrackedData.FeatureFlag(id = "flag2", startTimeMs = 3L),
                ),
            ),
            fakeExperimentTrackingService.trackCalls,
        )
        assertEquals(listOf("track_experiment", "track_feature_flag"), telemetryService.apiCalls)
    }

    @Test
    fun `a mixed untrack block records both kinds in declaration order in one call`() {
        sdkCallChecker.started.set(true)

        delegate.untrackExperiments {
            featureFlag("flag1", endedAt = 1L)
            experiment("exp1", endedAt = 2L)
            experiment("exp2", endedAt = 3L)
        }

        assertEquals(
            listOf(
                listOf(
                    UntrackedData(ExperimentKind.FEATURE_FLAG, "flag1", 1L),
                    UntrackedData(ExperimentKind.EXPERIMENT, "exp1", 2L),
                    UntrackedData(ExperimentKind.EXPERIMENT, "exp2", 3L),
                ),
            ),
            fakeExperimentTrackingService.untrackCalls,
        )
        assertEquals(listOf("untrack_experiment", "untrack_feature_flag"), telemetryService.apiCalls)
    }

    @Test
    fun `a block declaring several entries of one kind records its usage once`() {
        sdkCallChecker.started.set(true)

        delegate.trackExperiments {
            experiment("exp1", startedAt = 1L)
            experiment("exp2", startedAt = 2L)
            experiment("exp3", startedAt = 3L)
        }

        assertEquals(3, fakeExperimentTrackingService.trackedData.size)
        assertEquals(listOf("track_experiment"), telemetryService.apiCalls)
    }

    @Test
    fun `entries declared before a track block throws are recorded and the throwable is rethrown`() {
        sdkCallChecker.started.set(true)

        assertThrows(IllegalStateException::class.java) {
            delegate.trackExperiments {
                experiment("exp1", startedAt = 1L)
                error("consumer code failed")
            }
        }

        assertEquals(
            listOf<TrackedData>(TrackedData.Experiment(id = "exp1", startTimeMs = 1L, variant = null)),
            fakeExperimentTrackingService.trackedData,
        )
    }

    @Test
    fun `entries declared before an untrack block throws are recorded and the throwable is rethrown`() {
        sdkCallChecker.started.set(true)

        assertThrows(IllegalStateException::class.java) {
            delegate.untrackExperiments {
                experiment("exp1", endedAt = 1L)
                error("consumer code failed")
            }
        }

        assertEquals(
            listOf(UntrackedData(ExperimentKind.EXPERIMENT, "exp1", 1L)),
            fakeExperimentTrackingService.untrackedData,
        )
    }

    @Test
    fun `declarations made through a scope that escaped its block are ignored and logged`() {
        sdkCallChecker.started.set(true)
        var escapedTrackingScope: ExperimentTrackingScope? = null
        var escapedUntrackingScope: ExperimentUntrackingScope? = null

        delegate.trackExperiments {
            escapedTrackingScope = this
            experiment("exp1", startedAt = 1L)
        }
        delegate.untrackExperiments {
            escapedUntrackingScope = this
            experiment("exp1", endedAt = 2L)
        }
        checkNotNull(escapedTrackingScope).experiment("exp-escaped", startedAt = 3L)
        checkNotNull(escapedUntrackingScope).featureFlag("flag-escaped", endedAt = 4L)

        assertEquals(listOf("exp1"), fakeExperimentTrackingService.trackedData.map { it.id })
        assertEquals(listOf("exp1"), fakeExperimentTrackingService.untrackedData.map { it.id })
    }

    @Test
    fun `empty blocks do not throw, record no usage and leave nothing tracked`() {
        delegate.trackExperiments { }
        delegate.untrackExperiments { }

        sdkCallChecker.started.set(true)
        delegate.flushPendingCalls()

        delegate.trackExperiments { }
        delegate.untrackExperiments { }

        assertTrue(fakeExperimentTrackingService.trackCalls.isEmpty())
        assertTrue(fakeExperimentTrackingService.untrackCalls.isEmpty())
        assertTrue(telemetryService.apiCalls.isEmpty())
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
