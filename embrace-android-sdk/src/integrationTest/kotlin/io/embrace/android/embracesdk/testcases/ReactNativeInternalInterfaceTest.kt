package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSpanOfType
import io.embrace.android.embracesdk.assertions.findSpanSnapshotOfType
import io.embrace.android.embracesdk.assertions.findSpansByName
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.config.FakeProjectConfig
import io.embrace.android.embracesdk.internal.EmbraceInternalApi
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.toSessionPropertyAttributeName
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import io.embrace.android.embracesdk.testframework.assertions.assertMatches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validation of the internal API
 */
@RunWith(AndroidJUnit4::class)
internal class ReactNativeInternalInterfaceTest {

    private val instrumentedConfig = FakeInstrumentedConfig(project = FakeProjectConfig(
        appId = "abcde",
        appFramework = "react_native"
    ))

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

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
                    EmbraceInternalApi.getInstance().reactNativeInternalInterface.setReactNativeVersionNumber("28.9.1")
                    EmbraceInternalApi.getInstance().reactNativeInternalInterface.setReactNativeSdkVersion("1.2.3")
                    EmbraceInternalApi.getInstance().reactNativeInternalInterface.setJavaScriptPatchNumber("666")
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
                    EmbraceInternalApi.getInstance().reactNativeInternalInterface.setReactNativeVersionNumber("28.9.1")
                    EmbraceInternalApi.getInstance().reactNativeInternalInterface.setReactNativeSdkVersion("1.2.3")
                    EmbraceInternalApi.getInstance().reactNativeInternalInterface.setJavaScriptPatchNumber("666")
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
                    EmbraceInternalApi.getInstance().reactNativeInternalInterface.setReactNativeVersionNumber("28.9.1")
                    EmbraceInternalApi.getInstance().reactNativeInternalInterface.setReactNativeSdkVersion("1.2.3")
                    EmbraceInternalApi.getInstance().reactNativeInternalInterface.setJavaScriptPatchNumber("666")
                }

                recordSession {
                    EmbraceInternalApi.getInstance().reactNativeInternalInterface.setReactNativeVersionNumber("28.9.2")
                    EmbraceInternalApi.getInstance().reactNativeInternalInterface.setReactNativeSdkVersion("1.2.4")
                    EmbraceInternalApi.getInstance().reactNativeInternalInterface.setJavaScriptPatchNumber("999")
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

    @Test
    fun `react native action`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                recordSession {
                    EmbraceInternalApi.getInstance().reactNativeInternalInterface.logRnAction(
                        "MyAction",
                        1000,
                        5000,
                        mapOf("key" to "value"),
                        100,
                        "SUCCESS"
                    )
                }
            },
            assertAction = {
                val message = getSingleSessionEnvelope()
                val spans = message.findSpansByName("emb-rn-action")
                assertEquals(1, spans.size)

                val span = spans.single()
                assertEquals("emb-rn-action", span.name)
                assertEquals(1000L, span.startTimeNanos?.nanosToMillis())
                assertEquals(5000L, span.endTimeNanos?.nanosToMillis())

                span.attributes?.assertMatches(mapOf(
                    "emb.type" to "sys.rn_action",
                    "name" to "MyAction",
                    "outcome" to "SUCCESS",
                    "payload_size" to "100",
                    "key".toSessionPropertyAttributeName() to "value",
                ))
            }
        )
    }

    /*
    * The first view is logged and stored as a span, because we know that it ends when logRnView is called again.
    * The second view is logged as a span snapshot, because we know that it ends when the session ends.
    * */
    @Test
    fun `react native log RN view`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                recordSession {
                    EmbraceInternalApi.getInstance().reactNativeInternalInterface.logRnView("HomeScreen")
                    clock.tick(1000)
                    EmbraceInternalApi.getInstance().reactNativeInternalInterface.logRnView("DetailsScreen")
                }
            },
            assertAction = {
                val message = getSingleSessionEnvelope()
                val firstSpan = message.findSpanOfType(EmbType.Ux.View)
                val secondSpan = message.findSpanSnapshotOfType(EmbType.Ux.View)

                assertEquals("emb-screen-view", firstSpan.name)
                firstSpan.attributes?.assertMatches(mapOf(
                    "emb.type" to "ux.view",
                    "view.name" to "HomeScreen",
                ))

                assertEquals("emb-screen-view", secondSpan.name)
                secondSpan.attributes?.assertMatches(mapOf(
                    "emb.type" to "ux.view",
                    "view.name" to "DetailsScreen",
                ))
            }
        )
    }

    /*
    * The first view is logged and stored as a span, because we know that it ends when logRnView is called again.
    * The second view is logged as a span snapshot, because we know that it ends when the session ends.
    * */
    @Test
    fun `react native log RN view same name`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                recordSession {
                    EmbraceInternalApi.getInstance().reactNativeInternalInterface.logRnView("HomeScreen")
                    clock.tick(1000)
                    EmbraceInternalApi.getInstance().reactNativeInternalInterface.logRnView("HomeScreen")
                }
            },
            assertAction = {
                val message = getSingleSessionEnvelope()
                val firstSpan = message.findSpanOfType(EmbType.Ux.View)
                assertEquals("emb-screen-view", firstSpan.name)
                firstSpan.attributes?.assertMatches(mapOf(
                    "emb.type" to "ux.view",
                    "view.name" to "HomeScreen",
                ))

                val secondSpan = message.findSpanSnapshotOfType(EmbType.Ux.View)
                assertEquals("emb-screen-view", secondSpan.name)
                secondSpan.attributes?.assertMatches(mapOf(
                    "emb.type" to "ux.view",
                    "view.name" to "HomeScreen",
                ))
            }
        )
    }
}
