package io.embrace.android.embracesdk.testcases

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.config.FakeProjectConfig
import io.embrace.android.embracesdk.internal.EmbraceInternalApi
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.android.embracesdk.testframework.assertions.appFramework
import io.embrace.android.embracesdk.testframework.assertions.hostedPlatformVersion
import io.embrace.android.embracesdk.testframework.assertions.hostedSdkVersion
import io.embrace.android.embracesdk.testframework.assertions.javascriptPatchNumber
import io.embrace.android.embracesdk.testframework.assertions.reactNativeBundleId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Validation of the internal API
 */
@RunWith(AndroidJUnit4::class)
internal class ReactNativeInternalInterfaceTest {

    private val instrumentedConfig = FakeInstrumentedConfig(
        project = FakeProjectConfig(
            appId = "abcde",
            appFramework = "react_native"
        )
    )

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule {
        EmbraceSetupInterface(workersToFake = listOf(Worker.Background.NonIoRegWorker)).also {
            it.getFakedWorkerExecutor(Worker.Background.NonIoRegWorker).blockingMode = false
        }
    }

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
                assertNull(res.hostedSdkVersion)
                assertNull(res.hostedPlatformVersion)
                assertNull(res.javascriptPatchNumber)
                assertEquals("fakeRnBundleId", res.reactNativeBundleId)
            }
        )
    }

    @Test
    fun `react native bundle id is derived from the set JavaScript bundle url`() {
        val bundleFile = File.createTempFile("index.android.bundle", ".tmp").apply { deleteOnExit() }
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                recordSession {
                    EmbraceInternalApi.reactNativeInternalInterface.setJavaScriptBundleUrl(
                        context = ApplicationProvider.getApplicationContext(),
                        url = bundleFile.absolutePath,
                    )
                }
            },
            assertAction = {
                val res = checkNotNull(getSingleSessionEnvelope().resource)
                // ID is deterministic based on the bundle file that is created in this test
                assertEquals("D41D8CD98F00B204E9800998ECF8427E", res.reactNativeBundleId)
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
