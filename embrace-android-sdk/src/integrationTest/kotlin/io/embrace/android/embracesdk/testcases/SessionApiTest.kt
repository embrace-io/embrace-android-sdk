package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.opentelemetry.embFreeDiskBytes
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.recordSession
import io.embrace.android.embracesdk.toMap
import io.embrace.android.embracesdk.validatePayloadAgainstGoldenFile
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class SessionApiTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule {
        IntegrationTestRule.Harness().apply {
            overriddenConfigService.autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(diskUsageReportingEnabled = false)
        }
    }

    /**
     * Verifies that a session end message is sent.
     */
    @Test
    fun sessionEndMessageTest() {
        val startTime = testRule.harness.overriddenClock.now()
        with(testRule) {
            val message = harness.recordSession {
                embrace.setUserIdentifier("some id")
                embrace.setUserEmail("user@email.com")
                embrace.setUsername("John Doe")

                // add webview information
                val msg = ResourceReader.readResourceAsText("expected-webview-core-vital.json")
                embrace.trackWebViewPerformance("myWebView", msg)
            }
            checkNotNull(message)
            validatePayloadAgainstGoldenFile(message, "v2_session_expected.json")

            // validate snapshots separately, as the JSON diff is tricky to debug
            val snapshots = checkNotNull(message.data.spanSnapshots)
            assertEquals(2, snapshots.size)

            // validate network status span
            val networkStatusSpan = snapshots.single { it.name == "emb-network-status" }
            assertEquals(startTime, networkStatusSpan.startTimeNanos?.nanosToMillis())
            assertEquals("sys.network_status", networkStatusSpan.attributes?.findAttributeValue("emb.type"))

            // validate session span
            val sessionSpan = snapshots.single { it.name == "emb-session" }
            assertEquals(startTime, sessionSpan.startTimeNanos?.nanosToMillis())
            assertNotNull(sessionSpan.attributes?.findAttributeValue(SessionIncubatingAttributes.SESSION_ID.key))
            val attrs = checkNotNull(sessionSpan.attributes?.filterNot {
                ignoredAttributes.contains(it.key)
            }?.toMap())

            val expected = mapOf(
                "emb.cold_start" to "true",
                "emb.state" to "foreground",
                "emb.clean_exit" to "true",
                "emb.session_start_type" to "state",
                "emb.terminated" to "false",
                "emb.session_end_type" to "state",
                "emb.heartbeat_time_unix_nano" to "${startTime.millisToNanos()}",
                "emb.session_number" to "1",
                "emb.type" to "ux.session",
                "emb.error_log_count" to "0",
            )
            assertEquals(expected, attrs)
        }
    }

    private companion object {
        // Attributes that are unstable that we should not try to verify
        val ignoredAttributes = setOf(
            SessionIncubatingAttributes.SESSION_ID,
            embFreeDiskBytes.attributeKey
        ).map { it.key }
    }
}
