@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.testcases

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.internal.ApkToolsConfig
import io.embrace.android.embracesdk.recordSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
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
    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule(
        harnessSupplier = {
            IntegrationTestRule.Harness(appFramework = Embrace.AppFramework.UNITY)
        }
    )

    @Before
    fun setup() {
        ApkToolsConfig.IS_NETWORK_CAPTURE_DISABLED = false
    }

    @Test
    fun `unity without values should return defaults`() {
        with(testRule) {
            val session = harness.recordSession {

            }

            assertEquals(3, session?.appInfo?.appFramework)
            assertNull(session?.appInfo?.hostedSdkVersion)
            assertNull(session?.appInfo?.hostedPlatformVersion)
        }
    }

    @Test
    fun `unity methods work in current session`() {
        with(testRule) {
            val session = harness.recordSession {
                embrace.unityInternalInterface?.setUnityMetaData("28.9.1", "unity build id", "1.2.3")
            }

            assertEquals(3, session?.appInfo?.appFramework)
            assertEquals("28.9.1", checkNotNull(session?.appInfo?.hostedPlatformVersion))
            assertEquals("1.2.3", checkNotNull(session?.appInfo?.hostedSdkVersion))
            assertEquals("unity build id", checkNotNull(session?.appInfo?.buildGuid))
        }
    }

    @Test
    fun `unity metadata already present from previous session`() {
        with(testRule) {
            harness.recordSession {
                embrace.unityInternalInterface?.setUnityMetaData("28.9.1", "unity build id", "1.2.3")
            }

            val session = harness.recordSession {

            }

            assertEquals(3, session?.appInfo?.appFramework)
            assertEquals("28.9.1", checkNotNull(session?.appInfo?.hostedPlatformVersion))
            assertEquals("1.2.3", checkNotNull(session?.appInfo?.hostedSdkVersion))
            assertEquals("unity build id", checkNotNull(session?.appInfo?.buildGuid))
        }
    }

    @Test
    fun `unity values from current session override previous values`() {
        with(testRule) {
            harness.recordSession {
                embrace.unityInternalInterface?.setUnityMetaData("28.9.1", "unity build id", "1.2.3")
            }

            val session = harness.recordSession {
                embrace.unityInternalInterface?.setUnityMetaData("28.9.2", "new unity build id", "1.2.4")
            }

            assertEquals(3, session?.appInfo?.appFramework)
            assertEquals("28.9.2", checkNotNull(session?.appInfo?.hostedPlatformVersion))
            assertEquals("1.2.4", checkNotNull(session?.appInfo?.hostedSdkVersion))
            assertEquals("new unity build id", checkNotNull(session?.appInfo?.buildGuid))
        }
    }
}