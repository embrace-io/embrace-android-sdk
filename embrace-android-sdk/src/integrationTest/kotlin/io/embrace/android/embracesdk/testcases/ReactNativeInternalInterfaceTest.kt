@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.testcases

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.findSpanAttribute
import io.embrace.android.embracesdk.findSpanSnapshotsOfType
import io.embrace.android.embracesdk.findSpansOfType
import io.embrace.android.embracesdk.internal.ApkToolsConfig
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
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
internal class ReactNativeInternalInterfaceTest {
    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule(
        harnessSupplier = {
            IntegrationTestRule.Harness(appFramework = Embrace.AppFramework.REACT_NATIVE)
        }
    )

    @Before
    fun setup() {
        ApkToolsConfig.IS_NETWORK_CAPTURE_DISABLED = false
    }

    @Test
    fun `react native without values should return defaults`() {
        with(testRule) {
            val session = harness.recordSession {

            }

            assertEquals(2, session?.appInfo?.appFramework)
            assertNull(session?.appInfo?.reactNativeVersion)
            assertNull(session?.appInfo?.hostedSdkVersion)
            assertNull(session?.appInfo?.javaScriptPatchNumber)
        }
    }

    @Test
    fun `react native methods work in current session`() {
        with(testRule) {
            val session = harness.recordSession {
                embrace.reactNativeInternalInterface?.setReactNativeVersionNumber("28.9.1")
                embrace.reactNativeInternalInterface?.setReactNativeSdkVersion("1.2.3")
                embrace.reactNativeInternalInterface?.setJavaScriptPatchNumber("666")
            }

            assertEquals(2, session?.appInfo?.appFramework)
            assertEquals("28.9.1", checkNotNull(session?.appInfo?.reactNativeVersion))
            assertEquals("1.2.3", checkNotNull(session?.appInfo?.hostedSdkVersion))
            assertEquals("666", checkNotNull(session?.appInfo?.javaScriptPatchNumber))
        }
    }

    @Test
    fun `react native metadata already present from previous session`() {
        with(testRule) {
            harness.recordSession {
                embrace.reactNativeInternalInterface?.setReactNativeVersionNumber("28.9.1")
                embrace.reactNativeInternalInterface?.setReactNativeSdkVersion("1.2.3")
                embrace.reactNativeInternalInterface?.setJavaScriptPatchNumber("666")
            }

            val session = harness.recordSession {

            }

            assertEquals(2, session?.appInfo?.appFramework)
            assertEquals("28.9.1", checkNotNull(session?.appInfo?.reactNativeVersion))
            assertEquals("1.2.3", checkNotNull(session?.appInfo?.hostedSdkVersion))
            assertEquals("666", checkNotNull(session?.appInfo?.javaScriptPatchNumber))
        }
    }

    @Test
    fun `react native values from current session override previous values`() {
        with(testRule) {
            harness.recordSession {
                embrace.reactNativeInternalInterface?.setReactNativeVersionNumber("28.9.1")
                embrace.reactNativeInternalInterface?.setReactNativeSdkVersion("1.2.3")
                embrace.reactNativeInternalInterface?.setJavaScriptPatchNumber("666")
            }

            val session = harness.recordSession {
                embrace.reactNativeInternalInterface?.setReactNativeVersionNumber("28.9.2")
                embrace.reactNativeInternalInterface?.setReactNativeSdkVersion("1.2.4")
                embrace.reactNativeInternalInterface?.setJavaScriptPatchNumber("999")
            }

            assertEquals(2, session?.appInfo?.appFramework)
            assertEquals("28.9.2", checkNotNull(session?.appInfo?.reactNativeVersion))
            assertEquals("1.2.4", checkNotNull(session?.appInfo?.hostedSdkVersion))
            assertEquals("999", checkNotNull(session?.appInfo?.javaScriptPatchNumber))
        }
    }

    @Test
    fun `react native action`() {
        with(testRule) {
            val message = checkNotNull(harness.recordSession {
                embrace.reactNativeInternalInterface?.logRnAction(
                    "MyAction",
                    1000,
                    5000,
                    mapOf("key" to "value"),
                    100,
                    "SUCCESS"
                )
            })

            val spans = message.findSpansOfType(EmbType.System.ReactNativeAction)

            assertEquals(1, spans.size)

            val span = spans.single()

            assertEquals("emb-rn-action", span.name)
            assertEquals("sys.rn_action", span.findSpanAttribute("emb.type"))
            assertEquals("MyAction", span.findSpanAttribute("name"))
            assertEquals("SUCCESS", span.findSpanAttribute("outcome"))
            assertEquals("100", span.findSpanAttribute("payload_size"))
            assertEquals("value", span.findSpanAttribute("emb.properties.key"))
            assertEquals(1000, span.startTimeNanos.nanosToMillis())
            assertEquals(5000, span.endTimeNanos.nanosToMillis())
        }
    }

    /*
    * The first view is logged and stored as a span, because we know that it ends when logRnView is called again.
    * The second view is logged as a span snapshot, because we know that it ends when the session ends.
    * */
    @Test
    fun `react native log RN view`() {
        with(testRule) {
            val message = checkNotNull(harness.recordSession {
                embrace.reactNativeInternalInterface?.logRnView("HomeScreen")
                harness.overriddenClock.tick(1000)
                embrace.reactNativeInternalInterface?.logRnView("DetailsScreen")
            })

            val spans = message.findSpansOfType(EmbType.Ux.View)
            assertEquals(1, spans.size)

            val spanSnapshots = message.findSpanSnapshotsOfType(EmbType.Ux.View)
            assertEquals(1, spanSnapshots.size)

            val firstSpan = spans.single()
            val secondSpan = spanSnapshots.single()

            assertEquals("emb-screen-view", firstSpan.name)
            assertEquals("emb-screen-view", secondSpan.name)
            assertEquals("ux.view", firstSpan.findSpanAttribute("emb.type"))
            assertEquals("ux.view", secondSpan.findSpanAttribute("emb.type"))
            assertEquals("HomeScreen", firstSpan.findSpanAttribute("view.name"))
            assertEquals("DetailsScreen", secondSpan.findSpanAttribute("view.name"))
        }
    }

    /*
    * The first view is logged and stored as a span, because we know that it ends when logRnView is called again.
    * The second view is logged as a span snapshot, because we know that it ends when the session ends.
    * */
    @Test
    fun `react native log RN view same name`() {
        with(testRule) {
            val message = checkNotNull(harness.recordSession {
                embrace.reactNativeInternalInterface?.logRnView("HomeScreen")
                harness.overriddenClock.tick(1000)
                embrace.reactNativeInternalInterface?.logRnView("HomeScreen")
            })

            val spans = message.findSpansOfType(EmbType.Ux.View)
            assertEquals(1, spans.size)

            val spanSnapshots = message.findSpanSnapshotsOfType(EmbType.Ux.View)
            assertEquals(1, spanSnapshots.size)

            val firstSpan = spans.single()
            val secondSpan = spanSnapshots.single()

            assertEquals("emb-screen-view", firstSpan.name)
            assertEquals("emb-screen-view", secondSpan.name)
            assertEquals("ux.view", firstSpan.findSpanAttribute("emb.type"))
            assertEquals("ux.view", secondSpan.findSpanAttribute("emb.type"))
            assertEquals("HomeScreen", firstSpan.findSpanAttribute("view.name"))
            assertEquals("HomeScreen", secondSpan.findSpanAttribute("view.name"))
        }
    }
}