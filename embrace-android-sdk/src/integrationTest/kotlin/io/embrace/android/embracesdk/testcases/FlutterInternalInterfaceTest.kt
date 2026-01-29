package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.config.FakeProjectConfig
import io.embrace.android.embracesdk.internal.EmbraceInternalApi
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validation of the internal API
 */
@OptIn(ExperimentalApi::class)
@RunWith(AndroidJUnit4::class)
internal class FlutterInternalInterfaceTest {

    private val instrumentedConfig = FakeInstrumentedConfig(
        project = FakeProjectConfig(
            appId = "abcde",
            appFramework = "flutter"
        )
    )

    private var sessionStartTimeMs: Long = 0L

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Before
    fun before() {
        sessionStartTimeMs = 0L
    }

    @Test
    fun `flutter without values should return defaults`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val session = getSingleSessionEnvelope()
                val res = checkNotNull(session.resource)
                assertEquals(AppFramework.FLUTTER, res.appFramework)
                assertNull(res.hostedSdkVersion)
                assertNull(res.hostedPlatformVersion)
            }
        )
    }

    @Test
    fun `flutter methods work in current session`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                recordSession {
                    EmbraceInternalApi.flutterInternalInterface.setDartVersion("28.9.1")
                    EmbraceInternalApi.flutterInternalInterface.setEmbraceFlutterSdkVersion("1.2.3")
                }
            },
            assertAction = {
                val session = getSingleSessionEnvelope()
                val res = checkNotNull(session.resource)
                assertEquals(AppFramework.FLUTTER, res.appFramework)
                assertEquals("28.9.1", res.hostedPlatformVersion)
                assertEquals("1.2.3", res.hostedSdkVersion)
            }
        )
    }

    @Test
    fun `flutter metadata already present from previous session`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                recordSession {
                    EmbraceInternalApi.flutterInternalInterface.setDartVersion("28.9.1")
                    EmbraceInternalApi.flutterInternalInterface.setEmbraceFlutterSdkVersion("1.2.3")
                }
                recordSession()
            },
            assertAction = {
                val session = getSessionEnvelopes(2).last()
                val res = checkNotNull(session.resource)
                assertEquals(AppFramework.FLUTTER, res.appFramework)
                assertEquals("28.9.1", res.hostedPlatformVersion)
                assertEquals("1.2.3", res.hostedSdkVersion)
            }
        )
    }

    @Test
    fun `setting null is ignored`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                recordSession {
                    EmbraceInternalApi.flutterInternalInterface.setDartVersion("28.9.1")
                    EmbraceInternalApi.flutterInternalInterface.setEmbraceFlutterSdkVersion("1.2.3")
                }

                recordSession {
                    EmbraceInternalApi.flutterInternalInterface.setDartVersion(null)
                    EmbraceInternalApi.flutterInternalInterface.setEmbraceFlutterSdkVersion(null)
                }
            },
            assertAction = {
                val session = getSessionEnvelopes(2).last()
                val res = checkNotNull(session.resource)
                assertEquals(AppFramework.FLUTTER, res.appFramework)
                assertEquals("28.9.1", res.hostedPlatformVersion)
                assertEquals("1.2.3", res.hostedSdkVersion)
            }
        )
    }

    @Test
    fun `flutter values from current session override previous values`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                recordSession {
                    EmbraceInternalApi.flutterInternalInterface.setDartVersion("28.9.1")
                    EmbraceInternalApi.flutterInternalInterface.setEmbraceFlutterSdkVersion("1.2.3")
                }

                recordSession {
                    EmbraceInternalApi.flutterInternalInterface.setDartVersion("28.9.2")
                    EmbraceInternalApi.flutterInternalInterface.setEmbraceFlutterSdkVersion("1.2.4")
                }
            },
            assertAction = {
                val session = getSessionEnvelopes(2).last()
                val res = checkNotNull(session.resource)
                assertEquals(AppFramework.FLUTTER, res.appFramework)
                assertEquals("28.9.2", res.hostedPlatformVersion)
                assertEquals("1.2.4", res.hostedSdkVersion)
            }
        )
    }
}
