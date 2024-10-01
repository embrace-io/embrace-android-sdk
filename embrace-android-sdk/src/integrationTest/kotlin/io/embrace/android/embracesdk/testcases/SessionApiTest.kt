package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.opentelemetry.embFreeDiskBytes
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.testframework.assertions.toMap
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
        EmbraceSetupInterface().apply {
            overriddenConfigService.autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(diskUsageReportingEnabled = false)
        }
    }

    /**
     * Verifies that a session end message is sent.
     */
    @Test
    fun sessionEndMessageTest() {
        val startTime = testRule.action.clock.now()

        testRule.runTest(
            testCaseAction = {
                recordSession {
                    embrace.setUserIdentifier("some id")
                    embrace.setUserEmail("user@email.com")
                    embrace.setUsername("John Doe")

                    // add webview information
                    val msg = ResourceReader.readResourceAsText("expected-webview-core-vital.json")
                    embrace.trackWebViewPerformance("myWebView", msg)
                }
            },
            assertAction = {
                val message = getSingleSession()
                validatePayloadAgainstGoldenFile(message, "v2_session_expected.json")

                // validate snapshots separately, as the JSON diff is tricky to debug
                val snapshots = checkNotNull(message.data.spanSnapshots)
                assertEquals(1, snapshots.size)

                // validate network status span
                val networkStatusSpan = snapshots.single { it.name == "emb-network-status" }
                assertEquals(startTime, networkStatusSpan.startTimeNanos?.nanosToMillis())
                assertEquals("sys.network_status", networkStatusSpan.attributes?.findAttributeValue("emb.type"))

                // validate session span
                val spans = checkNotNull(message.data.spans)
                val sessionSpan = spans.single { it.name == "emb-session" }
                assertEquals(startTime, sessionSpan.startTimeNanos?.nanosToMillis())
                assertNotNull(sessionSpan.attributes?.findAttributeValue(SessionIncubatingAttributes.SESSION_ID.key))
                val attrs = checkNotNull(sessionSpan.attributes)
                val attributeKeys = attrs.map { it.key }
                validateExistenceOnly.forEach { key ->
                    attributeKeys.contains(key)
                }

                val attributesToCheck = attrs.filterNot {
                    ignoredAttributes.contains(it.key)
                }.toMap().toSortedMap()

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
                    "emb.usage.set_username" to "1",
                    "emb.usage.set_user_email" to "1",
                    "emb.usage.set_user_identifier" to "1",
                    "emb.private.sequence_id" to "4"
                ).toSortedMap()

                assertEquals(expected, attributesToCheck)
            }
        )
    }

    private companion object {
        // Attributes we want to know exist, but whose value we don't need to validate
        val validateExistenceOnly = setOf(
            SessionIncubatingAttributes.SESSION_ID.key,
            "emb.kotlin_on_classpath",
            "emb.okhttp3",
            "emb.process_identifier",
            "emb.is_emulator",
            "emb.okhttp3_on_classpath",
        )

        // Attributes that are unstable that we should not try to verify
        val ignoredAttributes = setOf(
            embFreeDiskBytes.attributeKey.key
        ).plus(validateExistenceOnly)
    }
}
