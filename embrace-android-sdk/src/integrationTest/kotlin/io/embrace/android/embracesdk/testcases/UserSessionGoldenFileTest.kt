package io.embrace.android.embracesdk.testcases

import android.app.ApplicationExitInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.TestAeiData
import io.embrace.android.embracesdk.fakes.setupFakeAeiData
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.UserSessionRemoteConfig
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeCrashDataSource
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.assertions.assertLogPayloadMatchesGoldenFile
import io.embrace.android.embracesdk.testframework.assertions.assertSessionSpanMatchesGoldenFile
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that the session span and log payload emitted by the SDK for basic scenarios match
 * known-good JSON golden files.
 */
@RunWith(AndroidJUnit4::class)
internal class UserSessionGoldenFileTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    /**
     * Asserts that sessions and logs can be associated via user session IDs.
     */
    @Test
    fun `basic session span and log payload association`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    embrace.logInfo("Hi")
                }
            },
            assertAction = {
                assertLogPayloadMatchesGoldenFile(
                    getSingleLogEnvelope(),
                    "user_session_basic_log.json",
                )
                assertSessionSpanMatchesGoldenFile(
                    getSingleSessionEnvelope(),
                    "user_session_basic_span.json",
                )
            }
        )
    }

    /**
     * Asserts that sessions and JVM crashes can be associated via user session IDs.
     */
    @Test
    fun `JVM crash association`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    simulateJvmUncaughtException(RuntimeException("boom"))
                }
            },
            assertAction = {
                assertSessionSpanMatchesGoldenFile(
                    getSingleSessionEnvelope(),
                    "user_session_jvm_crash_span.json",
                )
                assertLogPayloadMatchesGoldenFile(
                    getSingleLogEnvelope(),
                    "user_session_jvm_crash_log.json",
                )
            }
        )
    }

    /**
     * Asserts that sessions and NDK crashes can be associated via user session IDs.
     */
    @Test
    fun `NDK crash association`() {
        val sessionPartId = "1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d"
        val userSessionId = "2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e"
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    val nativeCrash = NativeCrashData(
                        nativeCrashId = "test-crash-report-id",
                        sessionPartId = sessionPartId,
                        userSessionId = userSessionId,
                        timestamp = clock.now(),
                        crash = null,
                        symbols = null,
                    )
                    findDataSource<NativeCrashDataSource>().sendNativeCrash(
                        nativeCrash = nativeCrash,
                        userSessionProperties = emptyMap(),
                        metadata = emptyMap(),
                    )
                }
            },
            assertAction = {
                assertLogPayloadMatchesGoldenFile(
                    getSingleLogEnvelope(),
                    "user_session_ndk_crash_log.json",
                )
            }
        )
    }


    /**
     * Asserts that sessions and AEI can be associated via user session IDs.
     */
    @Test
    fun `AEI association`() {
        val sessionPartId = "1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d"
        val userSessionId = "2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e"
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    val nativeCrash = NativeCrashData(
                        nativeCrashId = "test-crash-report-id",
                        sessionPartId = sessionPartId,
                        userSessionId = userSessionId,
                        timestamp = clock.now(),
                        crash = null,
                        symbols = null,
                    )
                    findDataSource<NativeCrashDataSource>().sendNativeCrash(
                        nativeCrash = nativeCrash,
                        userSessionProperties = emptyMap(),
                        metadata = emptyMap(),
                    )
                }
            },
            assertAction = {
                assertLogPayloadMatchesGoldenFile(
                    getSingleLogEnvelope(),
                    "user_session_ndk_crash_log.json",
                )
            }
        )
    }

    @Test
    fun `anr exit`() {
        testRule.runTest(
            setupAction = {
                val anr = TestAeiData(
                    ApplicationExitInfo.REASON_ANR,
                    0,
                    "aei",
                    "user input dispatch timed out",
                )
                setupFakeAeiData(listOf(anr.toAeiObject()))
            },
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                assertLogPayloadMatchesGoldenFile(
                    getSingleLogEnvelope(),
                    "user_session_aei_log.json",
                )
            }
        )
    }

    /**
     * Asserts that a user session can have multiple session parts.
     */
    @Test
    fun `user session with multiple parts`() {
        testRule.runTest(
            testCaseAction = {
                recordSession()
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                assertSessionSpanMatchesGoldenFile(
                    sessions[0],
                    "user_session_part_1.json",
                )
                assertSessionSpanMatchesGoldenFile(
                    sessions[1],
                    "user_session_part_2.json",
                )
            }
        )
    }

    /**
     * Non-default values for emb.user_session_max_duration_seconds and emb.user_session_inactivity_timeout_seconds
     * are serialized in the payload.
     */
    @Test
    fun `session span reflects configured max duration and inactivity timeout`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                userSession = UserSessionRemoteConfig(
                    maxDurationSeconds = 7200,
                    inactivityTimeoutSeconds = 600,
                ),
            ),
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                assertSessionSpanMatchesGoldenFile(
                    getSingleSessionEnvelope(),
                    "user_session_custom_timeouts.json",
                )
            }
        )
    }
}
