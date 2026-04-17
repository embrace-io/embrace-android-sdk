package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.toMap
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.semconv.EmbTelemetryAttributes
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.opentelemetry.kotlin.semconv.SessionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class UserSessionApiTest {

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
                    EmbSessionAttributes.EMB_COLD_START to "true",
                    EmbSessionAttributes.EMB_STATE to "foreground",
                    EmbSessionAttributes.EMB_CLEAN_EXIT to "true",
                    EmbSessionAttributes.EMB_SESSION_START_TYPE to "state",
                    EmbSessionAttributes.EMB_TERMINATED to "false",
                    EmbSessionAttributes.EMB_SESSION_END_TYPE to "state",
                    "emb.type" to "ux.session",
                    EmbSessionAttributes.EMB_ERROR_LOG_COUNT to "0",
                    "emb.usage.set_username" to "1",
                    "emb.usage.set_user_email" to "1",
                    "emb.usage.set_user_identifier" to "1",
                    EmbSessionAttributes.EMB_PRIVATE_SEQUENCE_ID to "5",
                    EmbSessionAttributes.EMB_STARTUP_DURATION to "0"
                ).toSortedMap()

                assertEquals(expected, attributesToCheck)
            }
        )
    }

    private companion object {
        // Attributes we want to know exist, but whose value we don't need to validate
        val validateExistenceOnly = setOf(
            SessionAttributes.SESSION_ID,
            EmbTelemetryAttributes.EMB_KOTLIN_ON_CLASSPATH,
            EmbTelemetryAttributes.EMB_OKHTTP3,
            EmbSessionAttributes.EMB_PROCESS_IDENTIFIER,
            EmbTelemetryAttributes.EMB_IS_EMULATOR,
            EmbTelemetryAttributes.EMB_OKHTTP3_ON_CLASSPATH,
            EmbSessionAttributes.EMB_HEARTBEAT_TIME_UNIX_NANO,
        )

        // Attributes that are unstable that we should not try to verify
        val ignoredAttributes = setOf(
            EmbSessionAttributes.EMB_DISK_FREE_BYTES,
            EmbSessionAttributes.EMB_SESSION_PART_ID,
            EmbSessionAttributes.EMB_USER_SESSION_ID,
            EmbSessionAttributes.EMB_USER_SESSION_NUMBER,
            EmbSessionAttributes.EMB_USER_SESSION_MAX_DURATION_SECONDS,
            EmbSessionAttributes.EMB_USER_SESSION_INACTIVITY_TIMEOUT_SECONDS,
            EmbSessionAttributes.EMB_USER_SESSION_PART_NUMBER,
            EmbSessionAttributes.EMB_USER_SESSION_START_TS,
        ).plus(validateExistenceOnly)
    }
}
