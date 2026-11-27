package io.embrace.android.embracesdk.testcases

import io.embrace.android.embracesdk.SessionStateEvent
import io.embrace.android.embracesdk.UserSessionListener
import io.embrace.android.embracesdk.assertions.toMap
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.semconv.EmbAppAttributes
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.semconv.EmbTelemetryAttributes
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(ParameterizedRobolectricTestRunner::class)
internal class UserSessionApiTest(
    private val otelSdkMode: OtelSdkMode,
) {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode = otelSdkMode)

    /**
     * Verifies that a session end message is sent.
     */
    @Suppress("DEPRECATION")
    @Test
    fun sessionEndMessageTest() {
        var startTime: Long = -1

        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    diskUsageCapture = false,
                    bgActivityCapture = true
                )
            ),
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

                // validate session part span
                val spans = checkNotNull(message.data.spans)
                val sessionPartSpan = spans.single { it.name == "emb-session" }
                assertEquals(startTime, sessionPartSpan.startTimeNanos?.nanosToMillis())
                assertNotNull(sessionPartSpan.attributes?.findAttributeValue(EmbSessionAttributes.EMB_USER_SESSION_ID))

                val attrs = checkNotNull(sessionPartSpan.attributes)
                val attributeKeys = attrs.map { it.key }
                validateExistenceOnly.forEach { key ->
                    assertTrue("'$key' not found in attrs", attributeKeys.contains(key))
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
                    EmbSessionAttributes.EMB_STARTUP_DURATION to "0",
                    EmbAppAttributes.EMB_APP_VERSION_STARTUP_COUNTER to "1"
                ).toSortedMap()

                assertEquals(expected, attributesToCheck)
            }
        )
    }

    @Test
    fun `user session listener added at start receives events once until removed`() {
        val events = CopyOnWriteArrayList<SessionStateEvent>()
        val listener = UserSessionListener { events.add(it) }
        lateinit var userSessionId: String
        lateinit var nextUserSessionId: String

        testRule.runTest(
            sdkConfig = {
                userSession {
                    addListener(listener)
                    addListener(listener)
                }
            },
            testCaseAction = {
                recordSession {
                    userSessionId = checkNotNull(embrace.currentUserSessionId)
                    embrace.endUserSession()
                    nextUserSessionId = checkNotNull(embrace.currentUserSessionId)
                    embrace.removeUserSessionListener(listener)
                    embrace.endUserSession()
                }
            },
            assertAction = {
                val sessionEvents = events.filter { it.userSessionId == userSessionId }
                assertTrue(sessionEvents.first() is SessionStateEvent.UserSessionActive)
                assertEquals(1, sessionEvents.count { it is SessionStateEvent.UserSessionActive })
                assertEquals(1, sessionEvents.count { it is SessionStateEvent.UserSessionEnded })

                // removed before the next user session ended
                assertTrue(events.none { it is SessionStateEvent.UserSessionEnded && it.userSessionId == nextUserSessionId })
            }
        )
    }

    internal companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()

        // Attributes we want to know exist, but whose value we don't need to validate
        val validateExistenceOnly = setOf(
            EmbTelemetryAttributes.EMB_KOTLIN_ON_CLASSPATH,
            EmbTelemetryAttributes.EMB_OKHTTP3,
            EmbSessionAttributes.EMB_PROCESS_IDENTIFIER,
            EmbTelemetryAttributes.EMB_IS_EMULATOR,
            EmbTelemetryAttributes.EMB_OKHTTP3_ON_CLASSPATH,
            EmbSessionAttributes.EMB_CLOCK_GNSS_DRIFT,
            EmbSessionAttributes.EMB_CLOCK_NETWORK_DRIFT,
        )

        // Attributes that are unstable that we should not try to verify
        val ignoredAttributes = setOf(
            EmbSessionAttributes.EMB_DISK_FREE_BYTES,
            EmbSessionAttributes.EMB_SESSION_PART_ID,
            EmbSessionAttributes.EMB_USER_SESSION_ID,
            EmbSessionAttributes.EMB_USER_SESSION_NUMBER,
            EmbSessionAttributes.EMB_USER_SESSION_MAX_DURATION_SECONDS,
            EmbSessionAttributes.EMB_USER_SESSION_INACTIVITY_TIMEOUT_SECONDS,
            EmbSessionAttributes.EMB_USER_SESSION_PART_INDEX,
            EmbSessionAttributes.EMB_SESSION_PART_NUMBER,
            EmbSessionAttributes.EMB_USER_SESSION_START_TS,
        ).plus(validateExistenceOnly)
    }
}
