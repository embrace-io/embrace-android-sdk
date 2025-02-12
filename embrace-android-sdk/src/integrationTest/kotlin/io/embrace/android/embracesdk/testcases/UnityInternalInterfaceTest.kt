package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.config.FakeProjectConfig
import io.embrace.android.embracesdk.internal.EmbraceInternalApi
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validation of the internal API
 */
@RunWith(AndroidJUnit4::class)
internal class UnityInternalInterfaceTest {

    private val instrumentedConfig = FakeInstrumentedConfig(project = FakeProjectConfig(
        appId = "abcde",
        appFramework = "unity"
    ))

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `unity without values should return defaults`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val session = getSingleSessionEnvelope()
                val res = checkNotNull(session.resource)
                assertEquals(AppFramework.UNITY, res.appFramework)
                assertNull(res.hostedSdkVersion)
                assertNull(res.hostedPlatformVersion)
            }
        )
    }

    @Test
    fun `unity methods work in current session`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                recordSession {
                    EmbraceInternalApi.getInstance().unityInternalInterface.setUnityMetaData(
                        "28.9.1",
                        "unity build id",
                        "1.2.3"
                    )
                }
            },
            assertAction = {
                val session = getSingleSessionEnvelope()
                val res = checkNotNull(session.resource)
                assertEquals(AppFramework.UNITY, res.appFramework)
                assertEquals("28.9.1", res.hostedPlatformVersion)
                assertEquals("1.2.3", res.hostedSdkVersion)
                assertEquals("unity build id", res.unityBuildId)
            }
        )
    }

    @Test
    fun `unity metadata already present from previous session`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                recordSession {
                    EmbraceInternalApi.getInstance().unityInternalInterface.setUnityMetaData(
                        "28.9.1",
                        "unity build id",
                        "1.2.3"
                    )
                }
                recordSession()
            },
            assertAction = {
                val session = getSessionEnvelopes(2).last()
                val res = checkNotNull(session.resource)
                assertEquals(AppFramework.UNITY, res.appFramework)
                assertEquals("28.9.1", res.hostedPlatformVersion)
                assertEquals("1.2.3", res.hostedSdkVersion)
                assertEquals("unity build id", res.unityBuildId)
            }
        )
    }

    @Test
    fun `unity values from current session override previous values`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                recordSession {
                    EmbraceInternalApi.getInstance().unityInternalInterface.setUnityMetaData(
                        "28.9.1",
                        "unity build id",
                        "1.2.3"
                    )
                }

                recordSession {
                    EmbraceInternalApi.getInstance().unityInternalInterface.setUnityMetaData(
                        "28.9.2",
                        "new unity build id",
                        "1.2.4"
                    )
                }
            },
            assertAction = {
                val session = getSessionEnvelopes(2).last()
                val res = checkNotNull(session.resource)
                assertEquals(AppFramework.UNITY, res.appFramework)
                assertEquals("28.9.2", res.hostedPlatformVersion)
                assertEquals("1.2.4", res.hostedSdkVersion)
                assertEquals("new unity build id", res.unityBuildId)
            }
        )
    }
}
