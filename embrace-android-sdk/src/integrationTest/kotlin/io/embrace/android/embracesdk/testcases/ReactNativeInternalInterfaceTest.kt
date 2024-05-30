package io.embrace.android.embracesdk.testcases

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.findAttributeValue
import io.embrace.android.embracesdk.fakes.fakeV2OtelBehavior
import io.embrace.android.embracesdk.findSpansByName
import io.embrace.android.embracesdk.internal.ApkToolsConfig
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
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
    val testRule: IntegrationTestRule = IntegrationTestRule {
        IntegrationTestRule.Harness(appFramework = Embrace.AppFramework.REACT_NATIVE)
    }

    @Before
    fun setup() {
        ApkToolsConfig.IS_NETWORK_CAPTURE_DISABLED = false
    }

    @Test
    fun `react native without values should return defaults`() {
        with(testRule) {
            val session = harness.recordSession {

            }

            val res = checkNotNull(session?.resource)
            assertEquals(EnvelopeResource.AppFramework.REACT_NATIVE, res.appFramework)
            assertNull(res.hostedPlatformVersion)
            assertNull(res.javascriptPatchNumber)
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

            val res = checkNotNull(session?.resource)
            assertEquals(EnvelopeResource.AppFramework.REACT_NATIVE, res.appFramework)
            assertEquals("28.9.1", res.hostedPlatformVersion)
            assertEquals("1.2.3", res.hostedSdkVersion)
            assertEquals("666", res.javascriptPatchNumber)
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

            val res = checkNotNull(session?.resource)
            assertEquals(EnvelopeResource.AppFramework.REACT_NATIVE, res.appFramework)
            assertEquals("28.9.1", res.hostedPlatformVersion)
            assertEquals("1.2.3", res.hostedSdkVersion)
            assertEquals("666", res.javascriptPatchNumber)
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

            val res = checkNotNull(session?.resource)
            assertEquals(EnvelopeResource.AppFramework.REACT_NATIVE, res.appFramework)
            assertEquals("28.9.2", res.hostedPlatformVersion)
            assertEquals("1.2.4", res.hostedSdkVersion)
            assertEquals("999", res.javascriptPatchNumber)
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

            val spans = message.findSpansByName("emb-rn-action")
            assertEquals(1, spans.size)

            val span = spans.single()
            val attrs = checkNotNull(span.attributes)
            assertEquals("emb-rn-action", span.name)
            assertEquals("sys.rn_action", attrs.findAttributeValue("emb.type"))
            assertEquals("MyAction", attrs.findAttributeValue("name"))
            assertEquals("SUCCESS", attrs.findAttributeValue("outcome"))
            assertEquals("100", attrs.findAttributeValue("payload_size"))
            assertEquals("value", attrs.findAttributeValue("emb.properties.key"))
            assertEquals(1000L, span.startTimeNanos?.nanosToMillis())
            assertEquals(5000L, span.endTimeNanos?.nanosToMillis())
        }
    }
}