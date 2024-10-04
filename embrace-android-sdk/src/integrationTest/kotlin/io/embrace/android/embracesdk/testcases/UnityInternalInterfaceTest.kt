package io.embrace.android.embracesdk.testcases

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import io.embrace.android.embracesdk.internal.payload.AppFramework
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Validation of the internal API
 */
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
@RunWith(AndroidJUnit4::class)
internal class UnityInternalInterfaceTest {

    @Suppress("DEPRECATION")
    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule {
        EmbraceSetupInterface(appFramework = Embrace.AppFramework.UNITY)
    }

    @Test
    fun `unity without values should return defaults`() {
        testRule.runTest(
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
            testCaseAction = {
                recordSession {
                    embrace.unityInternalInterface?.setUnityMetaData("28.9.1", "unity build id", "1.2.3")
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
            testCaseAction = {
                recordSession {
                    embrace.unityInternalInterface?.setUnityMetaData("28.9.1", "unity build id", "1.2.3")
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
            testCaseAction = {
                recordSession {
                    embrace.unityInternalInterface?.setUnityMetaData("28.9.1", "unity build id", "1.2.3")
                }

                recordSession {
                    embrace.unityInternalInterface?.setUnityMetaData("28.9.2", "new unity build id", "1.2.4")
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
