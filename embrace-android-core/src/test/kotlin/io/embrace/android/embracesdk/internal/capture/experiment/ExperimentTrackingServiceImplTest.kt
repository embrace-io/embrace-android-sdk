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

        // the dropped call does not invalidate the cached serialization or write the attribute again
        assertSame(records, service.getRecords())
        assertEquals(1, experimentAttributeWriteCount())
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

        // the dropped calls do not invalidate the cached serialization or write the attribute again
        assertSame(records, service.getRecords())
        assertEquals(1, experimentAttributeWriteCount())
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
    fun `a bulk call applies all entries in order with a single attribute write`() {
        service.track(
            listOf(
                TrackedData.Experiment(id = "id1", variant = null, startTimeMs = 100L),
                TrackedData.Experiment(id = "id2", variant = null, startTimeMs = 200L),
                TrackedData.Experiment(id = "id3", variant = null, startTimeMs = 300L),
            ),
        )
        service.assertRecordState("e:id1::100;e:id2::200;e:id3::300")
        assertEquals(1, experimentAttributeWriteCount())
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
    fun `an id that is empty after stripping is dropped without creating a record`() {
        service.track(
            listOf(
                TrackedData.Experiment(id = "", variant = null, startTimeMs = 100L),
                TrackedData.Experiment(id = " \t\r\n ", variant = null, startTimeMs = 200L),
            ),
        )
        service.assertRecordState(null)
    }

    @Test
    fun `ids and variants are stripped of ascii whitespace before validation, identity, and serialization`() {
        val service = serviceWithRemoteConfig(RemoteConfig(experimentIdMaxLength = 3))
        service.track(
            listOf(TrackedData.Experiment(id = " \t\na:b \r", variant = "\u000B v1 \u000C", startTimeMs = 100L)),
        )
        // the stripped id "a:b" passes the 3-char limit, is stored unescaped, and is escaped only at serialization
        service.assertRecordState("e:a%3Ab:v1:100")

        // untrack matches on the stripped id
        service.untrack(ExperimentKind.EXPERIMENT, listOf("a:b "), 200L)
        service.assertRecordState("e:a%3Ab:v1:100:200")
    }

    @Test
    fun `characters outside the six ascii whitespace code points are not stripped`() {
        service.track(
            listOf(
                TrackedData.Experiment(id = "\u00A0", variant = null, startTimeMs = 100L),
                TrackedData.Experiment(id = "\u001Fid\u0085", variant = "\u3000", startTimeMs = 200L),
            ),
        )
        service.assertRecordState("e:\u00A0::100;e:\u001Fid\u0085:\u3000:200")
    }

    @Test
    fun `null, empty, and whitespace-only variants are identical and absent in the output`() {
        service.track(
            listOf(
                TrackedData.Experiment(id = "id1", variant = null, startTimeMs = 100L),
                TrackedData.Experiment(id = "id2", variant = "", startTimeMs = 100L),
                TrackedData.Experiment(id = "id3", variant = " ", startTimeMs = 100L),
                TrackedData.Experiment(id = "id4", variant = "\t\r\n", startTimeMs = 100L),
            ),
        )
        service.assertRecordState("e:id1::100;e:id2::100;e:id3::100;e:id4::100")
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
    fun `making multiple calls in bulk results in one attribute write`() {
        service.bulkModify(
            listOf(
                ExperimentApiCall.Track(
                    listOf(
                        TrackedData.Experiment(id = "a", variant = null, startTimeMs = 100L),
                        TrackedData.Experiment(id = "b", variant = null, startTimeMs = 200L),
                    ),
                ),
                ExperimentApiCall.Untrack(ExperimentKind.EXPERIMENT, listOf("a"), 300L),
                ExperimentApiCall.Track(
                    listOf(TrackedData.FeatureFlag(id = "f", startTimeMs = 150L)),
                ),
                ExperimentApiCall.Untrack(ExperimentKind.FEATURE_FLAG, listOf("f"), 400L),
            ),
        )
        service.assertRecordState("e:a::100:300;e:b::200;f:f::150:400")
        assertEquals(1, experimentAttributeWriteCount())
    }

    @Test
    fun `bulk calls dedupes and invokes methods in the expected order`() {
        val replayed = serviceWithRemoteConfig(RemoteConfig())
        val events = listOf(
            // untracking before tracking is dropped, as it would be live
            ExperimentApiCall.Untrack(ExperimentKind.EXPERIMENT, listOf("id1"), 50L),
            ExperimentApiCall.Track(
                listOf(TrackedData.Experiment(id = "id1", variant = "v1", startTimeMs = 100L)),
            ),
            ExperimentApiCall.Track(
                listOf(TrackedData.Experiment(id = "id1", variant = "v2", startTimeMs = 200L)),
            ),
            ExperimentApiCall.Untrack(ExperimentKind.EXPERIMENT, listOf("id1"), 300L),
            ExperimentApiCall.Untrack(ExperimentKind.EXPERIMENT, listOf("id1"), 250L),
        )
        replayed.bulkModify(events)

        events.forEach { event ->
            when (event) {
                is ExperimentApiCall.Track -> service.track(event.data)
                is ExperimentApiCall.Untrack -> service.untrack(event.kind, event.ids, event.endTimeMs)
            }
        }

        assertEquals("e:id1:v1:100:300", replayed.getRecords())
        assertEquals(service.getRecords(), replayed.getRecords())
    }

    @Test
    fun `bulk calls validates and enforces limits`() {
        val service = serviceWithRemoteConfig(RemoteConfig(experimentMaxCount = 2))
        service.bulkModify(
            listOf(
                ExperimentApiCall.Track(
                    listOf(
                        TrackedData.Experiment(id = "id1", variant = null, startTimeMs = 100L),
                        TrackedData.Experiment(id = "", variant = null, startTimeMs = 150L),
                        TrackedData.Experiment(id = "id2", variant = null, startTimeMs = 200L),
                        TrackedData.Experiment(id = "id3", variant = null, startTimeMs = 300L),
                    ),
                ),
            ),
        )
        service.assertRecordState("e:id1::100;e:id2::200")
        assertEquals(listOf("experiments" to AppliedLimitType.DROP), telemetryService.appliedLimits)
    }

    @Test
    fun `bulk calls only updates experiments attributes if one of the calls actually changed something`() {
        service.bulkModify(emptyList())
        service.bulkModify(
            listOf(
                ExperimentApiCall.Track(
                    listOf(TrackedData.Experiment(id = " ", variant = null, startTimeMs = 100L)),
                ),
                ExperimentApiCall.Untrack(ExperimentKind.EXPERIMENT, listOf("unknown"), 200L),
            ),
        )
        service.assertRecordState(null)
        assertEquals(0, experimentAttributeWriteCount())
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

    private fun experimentAttributeWriteCount(): Int =
        destination.sessionPartAttributeWrites.count { it.first == EmbCommonAttributes.EMB_EXPERIMENTS }

    private fun ExperimentTrackingService.assertRecordState(expected: String?) {
        assertEquals(expected, getRecords())
        if (expected == null) {
            assertFalse(destination.attributes.containsKey(EmbCommonAttributes.EMB_EXPERIMENTS))
        } else {
            assertEquals(expected, destination.attributes[EmbCommonAttributes.EMB_EXPERIMENTS])
        }
    }
}
