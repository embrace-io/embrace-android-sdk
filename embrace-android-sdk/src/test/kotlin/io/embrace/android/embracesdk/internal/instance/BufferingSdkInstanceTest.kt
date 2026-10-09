package io.embrace.android.embracesdk.internal.instance

import io.embrace.android.embracesdk.experiments.TrackedExperiment
import io.embrace.android.embracesdk.experiments.TrackedFeatureFlag
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.internal.api.SdkApi
import io.embrace.android.embracesdk.internal.capture.experiment.ExperimentApiCall
import io.embrace.android.embracesdk.internal.capture.experiment.ExperimentKind
import io.embrace.android.embracesdk.internal.capture.experiment.TrackedData
import io.embrace.android.embracesdk.internal.config.resolved.ExperimentConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal class BufferingSdkInstanceTest {

    private lateinit var clock: FakeClock
    private lateinit var logger: FakeInternalLogger
    private lateinit var target: RecordingSdkApi
    private lateinit var replayed: MutableList<List<ExperimentApiCall>>
    private lateinit var instance: BufferingSdkInstance

    @Before
    fun setUp() {
        clock = FakeClock(currentTime = 1000L)
        logger = FakeInternalLogger(throwOnInternalError = false)
        target = RecordingSdkApi()
        replayed = mutableListOf()
        instance = BufferingSdkInstance(clock, logger)
    }

    @Test
    fun `experiment calls are replayed in one go when drained`() {
        instance.trackExperiments(
            listOf(
                instance.createExperiment("exp1", "v1", 10L),
                instance.createExperiment("exp2", null, 11L),
            ),
        )
        instance.trackExperiment("exp3", "v2", 12L)
        instance.trackFeatureFlag("flag1", "on", 13L)
        instance.trackFeatureFlags(listOf(instance.createFeatureFlag("flag2", null, 14L)))
        instance.untrackExperiments(listOf("exp1", "exp2"), 20L)
        instance.untrackExperiment("exp3", 21L)
        instance.untrackFeatureFlags(listOf("flag1"), 22L)
        instance.untrackFeatureFlag("flag2", 23L)
        drainExperiments()

        assertEquals(
            listOf(
                listOf(
                    ExperimentApiCall.Track(
                        listOf(TrackedData.experiment("exp1", 10L, "v1"), TrackedData.experiment("exp2", 11L, null)),
                    ),
                    ExperimentApiCall.Track(listOf(TrackedData.experiment("exp3", 12L, "v2"))),
                    ExperimentApiCall.Track(listOf(TrackedData.featureFlag("flag1", 13L, "on"))),
                    ExperimentApiCall.Track(listOf(TrackedData.featureFlag("flag2", 14L, null))),
                    ExperimentApiCall.Untrack(ExperimentKind.EXPERIMENT, listOf("exp1", "exp2"), 20L),
                    ExperimentApiCall.Untrack(ExperimentKind.EXPERIMENT, listOf("exp3"), 21L),
                    ExperimentApiCall.Untrack(ExperimentKind.FEATURE_FLAG, listOf("flag1"), 22L),
                    ExperimentApiCall.Untrack(ExperimentKind.FEATURE_FLAG, listOf("flag2"), 23L),
                ),
            ),
            replayed,
        )
        assertTrue(target.calls.isEmpty())
    }

    @Test
    fun `omitted experiment timestamps are captured when the call is buffered`() {
        val callTime = clock.now()
        instance.trackExperiment("exp1")
        instance.untrackFeatureFlag("flag1")
        clock.tick(500)
        drainExperiments()

        assertEquals(
            listOf(
                ExperimentApiCall.Track(listOf(TrackedData.experiment("exp1", callTime, null))),
                ExperimentApiCall.Untrack(ExperimentKind.FEATURE_FLAG, listOf("flag1"), callTime),
            ),
            replayed.single(),
        )
    }

    @Test
    fun `experiment buffer admits entries up to the absolute record limit, keeping the earliest`() {
        repeat(MAX_BUFFERED_EXPERIMENT_ENTRIES - 1) {
            instance.trackExperiment("exp-$it", startedAt = 1L)
        }
        instance.trackExperiments(
            listOf(instance.createExperiment("exp-kept", startedAt = 1L), instance.createExperiment("exp-dropped")),
        )
        instance.trackExperiment("exp-after-full", startedAt = 1L)

        drainExperiments()

        val ids = replayed.single().flatMap { (it as ExperimentApiCall.Track).data }.map { it.id }
        assertEquals(MAX_BUFFERED_EXPERIMENT_ENTRIES, ids.size)
        assertTrue(ids.contains("exp-kept"))
        assertFalse(ids.contains("exp-dropped"))
        assertFalse(ids.contains("exp-after-full"))
    }

    @Test
    fun `empty experiment calls are not buffered`() {
        instance.trackExperiments(emptyList())
        instance.untrackExperiments(emptyList())
        instance.trackFeatureFlags(emptyList())
        instance.untrackFeatureFlags(emptyList())
        drainExperiments()
        assertTrue(replayed.isEmpty())
    }

    @Test
    fun `experiment calls after draining are forwarded`() {
        drainExperiments()
        instance.trackExperiment("exp1", "v1", 10L)
        instance.untrackFeatureFlag("flag1", 20L)

        assertEquals(listOf("track_experiments:exp1", "untrack_feature_flags:flag1@20"), target.calls)
        assertTrue(replayed.isEmpty())
    }

    @Test
    fun `calls made during a replay are replayed in a later round, in order`() {
        instance.trackExperiment("exp1", startedAt = 1L)

        instance.drainExperimentCalls(target) { calls ->
            replayed.add(calls)
            if (replayed.size == 1) {
                instance.trackExperiment("exp2", startedAt = 2L)
            }
        }
        instance.trackExperiment("exp3", startedAt = 3L)

        assertEquals(
            listOf(
                listOf(ExperimentApiCall.Track(listOf(TrackedData.experiment("exp1", 1L, null)))),
                listOf(ExperimentApiCall.Track(listOf(TrackedData.experiment("exp2", 2L, null)))),
            ),
            replayed,
        )
        assertEquals(listOf("track_experiments:exp3"), target.calls)
    }

    @Test
    fun `callers do not wait for a replay to finish`() {
        val replayStarted = CountDownLatch(1)
        val releaseReplay = CountDownLatch(1)
        instance.trackExperiment("exp1", startedAt = 1L)

        val drainer = thread {
            instance.drainExperimentCalls(target) { calls ->
                replayed.add(calls)
                if (replayed.size == 1) {
                    replayStarted.countDown()
                    releaseReplay.await(5, TimeUnit.SECONDS)
                }
            }
        }
        assertTrue(replayStarted.await(5, TimeUnit.SECONDS))

        val caller = thread { instance.trackExperiment("exp2", startedAt = 2L) }
        caller.join(5000)
        assertFalse(caller.isAlive)

        releaseReplay.countDown()
        drainer.join(5000)
        assertFalse(drainer.isAlive)
        assertEquals(
            listOf(ExperimentApiCall.Track(listOf(TrackedData.experiment("exp2", 2L, null)))),
            replayed[1],
        )
        assertTrue(target.calls.isEmpty())
    }

    @Test
    fun `buffered experiment calls are not affected by later changes to the caller's data`() {
        val experiments = mutableListOf(instance.createExperiment("exp1", "v1", 1L))
        val ids = mutableListOf("exp1")
        instance.trackExperiments(experiments)
        instance.untrackExperiments(ids, 2L)

        experiments.clear()
        ids[0] = "changed"
        drainExperiments()

        assertEquals(
            listOf(
                ExperimentApiCall.Track(listOf(TrackedData.experiment("exp1", 1L, "v1"))),
                ExperimentApiCall.Untrack(ExperimentKind.EXPERIMENT, listOf("exp1"), 2L),
            ),
            replayed.single(),
        )
    }

    @Test
    fun `calls that cannot be buffered are dropped with a log`() {
        instance.addBreadcrumb("crumb")
        instance.logInfo("msg")
        assertEquals(1, logger.infoMessages.size)
        assertTrue(target.calls.isEmpty())
    }

    private fun drainExperiments() {
        instance.drainExperimentCalls(target) { replayed.add(it) }
    }

    private class RecordingSdkApi : SdkApi by NoopSdkInstance(FakeInternalLogger(), "") {
        val calls = mutableListOf<Any>()

        override fun trackExperiments(experiments: List<TrackedExperiment>) {
            calls.add("track_experiments:${experiments.joinToString { it.id }}")
        }

        override fun untrackExperiments(ids: List<String>, endedAt: Long?) {
            calls.add("untrack_experiments:${ids.joinToString()}@$endedAt")
        }

        override fun trackFeatureFlags(flags: List<TrackedFeatureFlag>) {
            calls.add("track_feature_flags:${flags.joinToString { it.id }}")
        }

        override fun untrackFeatureFlags(ids: List<String>, endedAt: Long?) {
            calls.add("untrack_feature_flags:${ids.joinToString()}@$endedAt")
        }
    }

    private companion object {
        private const val MAX_BUFFERED_EXPERIMENT_ENTRIES = ExperimentConfig.MAX_COUNT_LIMIT
    }
}
