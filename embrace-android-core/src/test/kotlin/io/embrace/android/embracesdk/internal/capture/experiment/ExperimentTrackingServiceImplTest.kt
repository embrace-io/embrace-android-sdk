package io.embrace.android.embracesdk.internal.capture.experiment

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeTelemetryDestination
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.createExperimentBehavior
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.semconv.EmbCommonAttributes
import io.embrace.android.embracesdk.semconv.ExperimentalSemconv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalSemconv::class)
internal class ExperimentTrackingServiceImplTest {

    private lateinit var telemetryService: FakeTelemetryService
    private lateinit var destination: FakeTelemetryDestination
    private lateinit var service: ExperimentTrackingService

    @Before
    fun setUp() {
        telemetryService = FakeTelemetryService()
        destination = FakeTelemetryDestination()
        service = ExperimentTrackingServiceImpl(
            configService = FakeConfigService(),
            telemetryService = telemetryService,
            telemetryDestination = destination,
        )
    }

    @Test
    fun `first track is reflected in getRecords`() {
        service.track(
            listOf(TrackedData.Experiment(id = "id1", variant = "v1", startTimeMs = 100L)),
        )
        service.assertRecordState("e:id1:v1:100")

        // repeated reads without a mutation return the same cached instance
        assertSame(service.getRecords(), service.getRecords())
    }

    @Test
    fun `re-tracking a known id with a different variant is dropped`() {
        service.track(
            listOf(TrackedData.Experiment(id = "id1", variant = "v1", startTimeMs = 100L)),
        )

        val records = service.getRecords()
        service.track(
            listOf(TrackedData.Experiment(id = "id1", variant = "v2", startTimeMs = 200L)),
        )
        service.assertRecordState("e:id1:v1:100")

        // the dropped call does not invalidate the cached serialization
        assertSame(records, service.getRecords())
    }

    @Test
    fun `untrack closes a record in place without disturbing insertion order`() {
        service.track(
            listOf(TrackedData.Experiment(id = "a", variant = null, startTimeMs = 100L)),
        )
        service.track(
            listOf(TrackedData.Experiment(id = "b", variant = null, startTimeMs = 200L)),
        )
        service.assertRecordState("e:a::100;e:b::200")

        service.untrack(ExperimentKind.EXPERIMENT, listOf("a"), 300L)
        service.assertRecordState("e:a::100:300;e:b::200")
    }

    @Test
    fun `calls to untrack an id not previously tracked with that kind has no effect`() {
        service.track(
            listOf(TrackedData.Experiment(id = "id1", variant = null, startTimeMs = 100L)),
        )

        val records = service.getRecords()
        service.untrack(ExperimentKind.EXPERIMENT, listOf("unknown"), 200L)
        service.untrack(ExperimentKind.FEATURE_FLAG, listOf("id1"), 200L)
        service.assertRecordState("e:id1::100")

        // the dropped calls do not invalidate the cached serialization
        assertSame(records, service.getRecords())
    }

    @Test
    fun `untracking an already-ended experiment has no effect`() {
        service.track(
            listOf(TrackedData.Experiment(id = "id1", variant = null, startTimeMs = 100L)),
        )
        service.untrack(ExperimentKind.EXPERIMENT, listOf("id1"), 200L)
        service.untrack(ExperimentKind.EXPERIMENT, listOf("id1"), 300L)
        service.assertRecordState("e:id1::100:200")
    }

    @Test
    fun `untrack closes a feature flag record`() {
        service.track(
            listOf(TrackedData.FeatureFlag(id = "flag1", startTimeMs = 100L)),
        )
        service.untrack(ExperimentKind.FEATURE_FLAG, listOf("flag1"), 200L)
        service.assertRecordState("f:flag1::100:200")
    }

    @Test
    fun `an experiment and a feature flag sharing an id are two independent records`() {
        service.track(
            listOf(TrackedData.Experiment(id = "id1", variant = null, startTimeMs = 100L)),
        )
        service.track(
            listOf(TrackedData.FeatureFlag(id = "id1", startTimeMs = 150L)),
        )
        service.assertRecordState("e:id1::100;f:id1::150")

        // untrack matches on kind as well as id, so only the feature flag record is closed
        service.untrack(ExperimentKind.FEATURE_FLAG, listOf("id1"), 200L)
        service.assertRecordState("e:id1::100;f:id1::150:200")
    }

    @Test
    fun `a bulk call applies all entries in order`() {
        service.track(
            listOf(
                TrackedData.Experiment(id = "id1", variant = null, startTimeMs = 100L),
                TrackedData.Experiment(id = "id2", variant = null, startTimeMs = 200L),
                TrackedData.Experiment(id = "id3", variant = null, startTimeMs = 300L),
            ),
        )
        service.assertRecordState("e:id1::100;e:id2::200;e:id3::300")
    }

    @Test
    fun `invalid entries in a bulk call are dropped while valid entries are applied`() {
        service.track(
            listOf(
                TrackedData.Experiment(id = "id1", variant = null, startTimeMs = 100L),
                TrackedData.Experiment(id = "", variant = null, startTimeMs = 200L),
                TrackedData.Experiment(id = "id2", variant = null, startTimeMs = 300L),
            ),
        )
        service.assertRecordState("e:id1::100;e:id2::300")
    }

    @Test
    fun `a blank id is dropped without creating a record`() {
        service.track(
            listOf(TrackedData.Experiment(id = "", variant = null, startTimeMs = 100L)),
        )
        service.assertRecordState(null)
    }

    @Test
    fun `an id longer than the max length is dropped silently`() {
        val service = serviceWithRemoteConfig(RemoteConfig(experimentIdMaxLength = 5))
        service.track(
            listOf(TrackedData.Experiment(id = "123456", variant = null, startTimeMs = 100L)),
        )
        service.assertRecordState(null)
    }

    @Test
    fun `a variant longer than the max length is dropped silently`() {
        val service = serviceWithRemoteConfig(RemoteConfig(experimentVariantMaxLength = 5))
        service.track(
            listOf(TrackedData.Experiment(id = "id1", variant = "123456", startTimeMs = 100L)),
        )
        service.assertRecordState(null)
    }

    @Test
    fun `tracking a new id is dropped once the record cap is reached`() {
        val service = serviceWithRemoteConfig(RemoteConfig(experimentMaxCount = 2))
        service.track(
            listOf(
                TrackedData.Experiment(id = "id1", variant = null, startTimeMs = 100L),
                TrackedData.Experiment(id = "id2", variant = null, startTimeMs = 200L),
            ),
        )
        service.track(
            listOf(TrackedData.Experiment(id = "id3", variant = null, startTimeMs = 300L)),
        )
        service.assertRecordState("e:id1::100;e:id2::200")
        assertEquals(listOf("experiments" to AppliedLimitType.DROP), telemetryService.appliedLimits)
    }

    @Test
    fun `untracking does not free a slot because ended records count against the cap`() {
        val service = serviceWithRemoteConfig(RemoteConfig(experimentMaxCount = 2))
        service.track(
            listOf(
                TrackedData.Experiment(id = "id1", variant = null, startTimeMs = 100L),
                TrackedData.Experiment(id = "id2", variant = null, startTimeMs = 200L),
            ),
        )
        service.untrack(ExperimentKind.EXPERIMENT, listOf("id1"), 150L)
        service.track(
            listOf(TrackedData.Experiment(id = "id3", variant = null, startTimeMs = 300L)),
        )
        service.assertRecordState("e:id1::100:150;e:id2::200")
        assertEquals(listOf("experiments" to AppliedLimitType.DROP), telemetryService.appliedLimits)
    }

    @Test
    fun `re-tracking a known id and untracking are never blocked by being at the cap`() {
        val service = serviceWithRemoteConfig(RemoteConfig(experimentMaxCount = 2))
        service.track(
            listOf(
                TrackedData.Experiment(id = "id1", variant = "v1", startTimeMs = 100L),
                TrackedData.Experiment(id = "id2", variant = null, startTimeMs = 200L),
            ),
        )
        // re-tracking a known id while at the cap is a no-op, not a drop
        service.track(
            listOf(TrackedData.Experiment(id = "id1", variant = "v2", startTimeMs = 999L)),
        )
        service.assertRecordState("e:id1:v1:100;e:id2::200")

        // untracking while at the cap is never blocked
        service.untrack(ExperimentKind.EXPERIMENT, listOf("id1"), 300L)
        service.assertRecordState("e:id1:v1:100:300;e:id2::200")
        assertTrue(telemetryService.appliedLimits.none { it.first == "experiments" })
    }

    @Test
    fun `getRecords is null when nothing has ever been tracked`() {
        service.assertRecordState(null)
    }

    @Test
    fun `serialization order follows tracking order irrespective of kind`() {
        service.track(
            listOf(TrackedData.Experiment(id = "id1", variant = null, startTimeMs = 100L)),
        )
        service.track(
            listOf(TrackedData.FeatureFlag(id = "id2", startTimeMs = 200L)),
        )
        service.track(
            listOf(TrackedData.Experiment(id = "id3", variant = null, startTimeMs = 300L)),
        )
        service.assertRecordState("e:id1::100;f:id2::200;e:id3::300")
    }

    private fun serviceWithRemoteConfig(remoteConfig: RemoteConfig): ExperimentTrackingService =
        ExperimentTrackingServiceImpl(
            configService = FakeConfigService(experimentBehavior = createExperimentBehavior(remoteConfig)),
            telemetryService = telemetryService,
            telemetryDestination = destination,
        )

    private fun ExperimentTrackingService.assertRecordState(expected: String?) {
        assertEquals(expected, getRecords())
        if (expected == null) {
            assertFalse(destination.attributes.containsKey(EmbCommonAttributes.EMB_EXPERIMENTS))
        } else {
            assertEquals(expected, destination.attributes[EmbCommonAttributes.EMB_EXPERIMENTS])
        }
    }
}
