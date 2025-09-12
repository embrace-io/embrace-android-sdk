package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.assertions.toMap
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.attrs.embFreeDiskBytes
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.opentelemetry.kotlin.semconv.IncubatingApi
import io.embrace.opentelemetry.kotlin.semconv.SessionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(IncubatingApi::class)
@RunWith(AndroidJUnit4::class)
internal class SessionApiTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    /**
     * Verifies that a session end message is sent.
     */
    @Suppress("DEPRECATION")
    @Test
    fun sessionEndMessageTest() {
        var startTime: Long = -1

        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(enabledFeatures = FakeEnabledFeatureConfig(diskUsageCapture = false, bgActivityCapture = true)),
            testCaseAction = {
                startTime = recordSession {
                    embrace.setUserIdentifier("some id")
                    embrace.setUserEmail("user@email.com")
                    embrace.setUsername("John Doe")

                    // add webview information
                    val msg = ResourceReader.readResourceAsText("expected-webview-core-vital.json")
                    embrace.trackWebViewPerformance("myWebView", msg)
                }.startTimeMs
            },
            assertAction = {
                val message = getSingleSessionEnvelope()
                validatePayloadAgainstGoldenFile(message, "v2_session_expected.json")

                // validate snapshots separately, as the JSON diff is tricky to debug
                val snapshots = checkNotNull(message.data.spanSnapshots)
                assertEquals(1, snapshots.size)

                // validate expected in-process spans
                checkNotNull(snapshots.single { it.name == "emb-network-status" })

                // validate session span
                val spans = checkNotNull(message.data.spans)
                val sessionSpan = spans.single { it.name == "emb-session" }
                assertEquals(startTime, sessionSpan.startTimeNanos?.nanosToMillis())
                assertNotNull(sessionSpan.attributes?.findAttributeValue(SessionAttributes.SESSION_ID))

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
                    "emb.session_number" to "1",
                    "emb.type" to "ux.session",
                    "emb.error_log_count" to "0",
                    "emb.usage.set_username" to "1",
                    "emb.usage.set_user_email" to "1",
                    "emb.usage.set_user_identifier" to "1",
                    "emb.private.sequence_id" to "5",
                    "emb.startup_duration" to "0"
                ).toSortedMap()

                assertEquals(expected, attributesToCheck)
            }
        )
    }

    private companion object {
        // Attributes we want to know exist, but whose value we don't need to validate
        val validateExistenceOnly = setOf(
            SessionAttributes.SESSION_ID,
            "emb.kotlin_on_classpath",
            "emb.okhttp3",
            "emb.process_identifier",
            "emb.is_emulator",
            "emb.okhttp3_on_classpath",
            "emb.heartbeat_time_unix_nano",
        )

        // Attributes that are unstable that we should not try to verify
        val ignoredAttributes = setOf(
            embFreeDiskBytes.name
        ).plus(validateExistenceOnly)
    }
}
