package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.RobolectricTest
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
internal class ReactNativeInternalInterfaceTest: RobolectricTest() {

    private val instrumentedConfig = FakeInstrumentedConfig(
        project = FakeProjectConfig(
            appId = "abcde",
            appFramework = "react_native"
        )
    )

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `react native without values should return defaults`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val session = getSingleSessionEnvelope()
                val res = checkNotNull(session.resource)
                assertEquals(AppFramework.REACT_NATIVE, res.appFramework)
                assertNull(res.hostedPlatformVersion)
                assertNull(res.javascriptPatchNumber)
            }
        )
    }

    @Test
    fun `react native methods work in current session`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                recordSession {
                    EmbraceInternalApi.reactNativeInternalInterface.setReactNativeVersionNumber("28.9.1")
                    EmbraceInternalApi.reactNativeInternalInterface.setReactNativeSdkVersion("1.2.3")
                    EmbraceInternalApi.reactNativeInternalInterface.setJavaScriptPatchNumber("666")
                }
            },
            assertAction = {
                val session = getSingleSessionEnvelope()
                val res = checkNotNull(session.resource)
                assertEquals(AppFramework.REACT_NATIVE, res.appFramework)
                assertEquals("28.9.1", res.hostedPlatformVersion)
                assertEquals("1.2.3", res.hostedSdkVersion)
                assertEquals("666", res.javascriptPatchNumber)
            }
        )
    }

    @Test
    fun `react native metadata already present from previous session`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                recordSession {
                    EmbraceInternalApi.reactNativeInternalInterface.setReactNativeVersionNumber("28.9.1")
                    EmbraceInternalApi.reactNativeInternalInterface.setReactNativeSdkVersion("1.2.3")
                    EmbraceInternalApi.reactNativeInternalInterface.setJavaScriptPatchNumber("666")
                }

                recordSession()
            },
            assertAction = {
                val session = getSessionEnvelopes(2).last()
                val res = checkNotNull(session.resource)
                assertEquals(AppFramework.REACT_NATIVE, res.appFramework)
                assertEquals("28.9.1", res.hostedPlatformVersion)
                assertEquals("1.2.3", res.hostedSdkVersion)
                assertEquals("666", res.javascriptPatchNumber)
            }
        )
    }

    @Test
    fun `react native values from current session override previous values`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                recordSession {
                    EmbraceInternalApi.reactNativeInternalInterface.setReactNativeVersionNumber("28.9.1")
                    EmbraceInternalApi.reactNativeInternalInterface.setReactNativeSdkVersion("1.2.3")
                    EmbraceInternalApi.reactNativeInternalInterface.setJavaScriptPatchNumber("666")
                }

                recordSession {
                    EmbraceInternalApi.reactNativeInternalInterface.setReactNativeVersionNumber("28.9.2")
                    EmbraceInternalApi.reactNativeInternalInterface.setReactNativeSdkVersion("1.2.4")
                    EmbraceInternalApi.reactNativeInternalInterface.setJavaScriptPatchNumber("999")
                }
            },
            assertAction = {
                val session = getSessionEnvelopes(2).last()

                val res = checkNotNull(session.resource)
                assertEquals(AppFramework.REACT_NATIVE, res.appFramework)
                assertEquals("28.9.2", res.hostedPlatformVersion)
                assertEquals("1.2.4", res.hostedSdkVersion)
                assertEquals("999", res.javascriptPatchNumber)
            }
        )
    }
}
