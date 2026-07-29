package io.embrace.android.embracesdk.internal.capture.experiment

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.createExperimentBehavior
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class ExperimentTrackingServiceImplTest {

    private lateinit var telemetryService: FakeTelemetryService
    private lateinit var service: ExperimentTrackingService

    @Before
    fun setUp() {
        telemetryService = FakeTelemetryService()
        service = ExperimentTrackingServiceImpl(
            configService = FakeConfigService(),
            telemetryService = telemetryService,
        )
    }

    @Test
    fun `first track is reflected in getRecords`() {
        service.track(
            listOf(TrackedData.Experiment(id = "id1", variant = "v1", startTimeMs = 100L)),
        )
        assertEquals("e:id1:v1:100", service.getRecords())

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
        assertEquals("e:id1:v1:100", service.getRecords())

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

        assertEquals("e:a::100;e:b::200", service.getRecords())
        service.untrack(listOf("a"), 300L)
        assertEquals("e:a::100:300;e:b::200", service.getRecords())
    }

    @Test
    fun `calls to untrack an id not previously tracked has no effect`() {
        service.track(
            listOf(TrackedData.Experiment(id = "id1", variant = null, startTimeMs = 100L)),
        )

        val records = service.getRecords()
        service.untrack(listOf("unknown"), 200L)
        assertEquals("e:id1::100", service.getRecords())

        // the dropped call does not invalidate the cached serialization
        assertSame(records, service.getRecords())
    }

    @Test
    fun `untracking an already-ended experiment has no effect`() {
        service.track(
            listOf(TrackedData.Experiment(id = "id1", variant = null, startTimeMs = 100L)),
        )
        service.untrack(listOf("id1"), 200L)
        service.untrack(listOf("id1"), 300L)
        assertEquals("e:id1::100:200", service.getRecords())
    }

    @Test
    fun `untrack closes a feature flag record`() {
        service.track(
            listOf(TrackedData.FeatureFlag(id = "flag1", startTimeMs = 100L)),
        )
        service.untrack(listOf("flag1"), 200L)
        assertEquals("f:flag1::100:200", service.getRecords())
    }

    @Test
    fun `an id tracked as an experiment cannot be re-tracked as a feature flag`() {
        service.track(
            listOf(TrackedData.Experiment(id = "id1", variant = null, startTimeMs = 100L)),
        )
        service.track(
            listOf(TrackedData.FeatureFlag(id = "id1", startTimeMs = 150L)),
        )
        assertEquals("e:id1::100", service.getRecords())
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
        assertEquals("e:id1::100;e:id2::200;e:id3::300", service.getRecords())
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
        assertEquals("e:id1::100;e:id2::300", service.getRecords())
    }

    @Test
    fun `a blank id is dropped without creating a record`() {
        service.track(
            listOf(TrackedData.Experiment(id = "", variant = null, startTimeMs = 100L)),
        )
        assertNull(service.getRecords())
    }

    @Test
    fun `an id longer than the max length is dropped silently`() {
        val service = serviceWithRemoteConfig(RemoteConfig(maxExperimentIdLength = 5))
        service.track(
            listOf(TrackedData.Experiment(id = "123456", variant = null, startTimeMs = 100L)),
        )
        assertNull(service.getRecords())
    }

    @Test
    fun `a variant longer than the max length is dropped silently`() {
        val service = serviceWithRemoteConfig(RemoteConfig(maxExperimentVariantLength = 5))
        service.track(
            listOf(TrackedData.Experiment(id = "id1", variant = "123456", startTimeMs = 100L)),
        )
        assertNull(service.getRecords())
    }

    @Test
    fun `tracking a new id is dropped once the active cap is reached`() {
        val service = serviceWithRemoteConfig(RemoteConfig(maxExperimentCount = 2))
        service.track(
            listOf(
                TrackedData.Experiment(id = "id1", variant = null, startTimeMs = 100L),
                TrackedData.Experiment(id = "id2", variant = null, startTimeMs = 200L),
            ),
        )
        service.track(
            listOf(TrackedData.Experiment(id = "id3", variant = null, startTimeMs = 300L)),
        )
        assertEquals("e:id1::100;e:id2::200", service.getRecords())
        assertEquals(listOf("experiment" to AppliedLimitType.DROP), telemetryService.appliedLimits)
    }

    @Test
    fun `untracking an active id frees a slot for a new id`() {
        val service = serviceWithRemoteConfig(RemoteConfig(maxExperimentCount = 2))
        service.track(
            listOf(
                TrackedData.Experiment(id = "id1", variant = null, startTimeMs = 100L),
                TrackedData.Experiment(id = "id2", variant = null, startTimeMs = 200L),
            ),
        )
        service.untrack(listOf("id1"), 150L)
        service.track(
            listOf(TrackedData.Experiment(id = "id3", variant = null, startTimeMs = 300L)),
        )
        assertEquals("e:id1::100:150;e:id2::200;e:id3::300", service.getRecords())
    }

    @Test
    fun `re-tracking a known id and untracking are never blocked by being at the cap`() {
        val service = serviceWithRemoteConfig(RemoteConfig(maxExperimentCount = 2))
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
        assertEquals("e:id1:v1:100;e:id2::200", service.getRecords())

        // untracking while at the cap is never blocked
        service.untrack(listOf("id1"), 300L)
        assertEquals("e:id1:v1:100:300;e:id2::200", service.getRecords())
        assertTrue(telemetryService.appliedLimits.none { it.first == "experiment" })
    }

    @Test
    fun `ended records beyond the active cap remain in the serialized output`() {
        val service = serviceWithRemoteConfig(RemoteConfig(maxExperimentCount = 1))
        service.track(
            listOf(TrackedData.Experiment(id = "id1", variant = null, startTimeMs = 100L)),
        )
        service.untrack(listOf("id1"), 150L)
        service.track(
            listOf(TrackedData.Experiment(id = "id2", variant = null, startTimeMs = 200L)),
        )
        service.untrack(listOf("id2"), 250L)
        service.track(
            listOf(TrackedData.Experiment(id = "id3", variant = null, startTimeMs = 300L)),
        )
        assertEquals(
            "e:id1::100:150;e:id2::200:250;e:id3::300",
            service.getRecords(),
        )
    }

    @Test
    fun `getRecords is null when nothing has ever been tracked`() {
        assertNull(service.getRecords())
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
        assertEquals("e:id1::100;f:id2::200;e:id3::300", service.getRecords())
    }

    private fun serviceWithRemoteConfig(remoteConfig: RemoteConfig): ExperimentTrackingService =
        ExperimentTrackingServiceImpl(
            configService = FakeConfigService(experimentBehavior = createExperimentBehavior(remoteConfig)),
            telemetryService = telemetryService,
        )
}
