package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.LastRunEndState
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validation of the basic and miscellaneous functionality of the Android SDK
 */
@RunWith(AndroidJUnit4::class)
internal class PublicApiTest {

    companion object {
        val validPattern = Regex("^00-" + "[0-9a-fA-F]{32}" + "-" + "[0-9a-fA-F]{16}" + "-01$")
    }

    private val instrumentedConfig = FakeInstrumentedConfig(enabledFeatures = FakeEnabledFeatureConfig(networkSpanForwarding = true))

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `SDK start defaults to native app framework`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                assertTrue(embrace.isStarted)
            }
        )
    }

    @Test
    fun `getCurrentUserSessionId returns null when SDK is not started`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            startSdk = false,
            testCaseAction = {
                assertNull(embrace.currentUserSessionId)
            }
        )
    }

    @Test
    fun `getCurrentUserSessionId returns sessionId when SDK is started and foreground session is active`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                recordSession {
                    assertNotNull(embrace.currentUserSessionId)
                }
            }
        )
    }

    @Test
    fun `getCurrentUserSessionId returns sessionId when SDK is started and background session is active`() {
        var foregroundSessionId: String? = null
        var backgroundSessionId: String? = null
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(backgroundActivityConfig = BackgroundActivityRemoteConfig(100f)),
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                recordSession {
                    foregroundSessionId = embrace.currentUserSessionId
                }
                backgroundSessionId = embrace.currentUserSessionId
            },
            assertAction = {
                assertNotNull(backgroundSessionId)
                assertEquals(foregroundSessionId, backgroundSessionId)
            }
        )
    }

    @Test
    fun `currentUserSessionId is stable across FG to BG to FG transition`() {
        val ids = mutableListOf<String?>()
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                recordSession {
                    ids.add(embrace.currentUserSessionId)
                }
                ids.add(embrace.currentUserSessionId)
                recordSession {
                    ids.add(embrace.currentUserSessionId)
                }
            },
            assertAction = {
                assertEquals(3, ids.size)
                assertTrue(ids.all { it != null })
                assertEquals(ids[0], ids[1])
                assertEquals(ids[1], ids[2])
            }
        )
    }

    @Test
    fun `getLastRunEndState() behave as expected`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            preSdkStartAction = {
                assertEquals(LastRunEndState.INVALID, embrace.lastRunEndState)
            },
            testCaseAction = {
                assertEquals(LastRunEndState.CLEAN_EXIT, embrace.lastRunEndState)
            }
        )
    }

    @Test
    fun `ensure all generated W3C traceparent conforms to the expected format`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                repeat(100) {
                    assertTrue(validPattern.matches(checkNotNull(embrace.generateW3cTraceparent())))
                }
            }
        )
    }

    @Test
    fun `test sdk time`() {
        testRule.runTest(
            testCaseAction = {
                assertEquals(clock.now(), embrace.getSdkCurrentTimeMs())
                clock.tick()
                assertEquals(clock.now(), embrace.getSdkCurrentTimeMs())
            }
        )
    }
}
