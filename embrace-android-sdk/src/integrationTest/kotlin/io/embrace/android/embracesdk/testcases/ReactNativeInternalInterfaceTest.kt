@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.testcases

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.findSpanAttribute
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
}