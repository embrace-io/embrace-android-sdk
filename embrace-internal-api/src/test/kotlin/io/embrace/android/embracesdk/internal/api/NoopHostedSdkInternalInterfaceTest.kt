package io.embrace.android.embracesdk.internal.api

import android.app.Application
import io.embrace.android.embracesdk.internal.api.delegate.NoopHostedSdkInternalInterface
import org.junit.Assert.assertFalse
import org.junit.Test

internal class NoopHostedSdkInternalInterfaceTest {

    @Test
    fun `check isNetworkSpanForwardingEnabled before SDK starts`() {
        assertFalse(NoopHostedSdkInternalInterface.isNetworkSpanForwardingEnabled())
    }

    @Test
    fun `check no errors thrown when invoked`() {
        with(NoopHostedSdkInternalInterface) {
            addEnvelopeResource("key", "value")
            setHostedSdkVersions("version", "version")
            logUnhandledJsException("name", "message", "type", "stacktrace")
            setJavaScriptPatchNumber("number")
            setJavaScriptBundleUrl(Application(), "url")
            setUnityBuildGuid("buildGuid")
            logUnhandledUnityException("name", "message", "stacktrace")
            logHandledUnityException("name", "message", "stacktrace")
        }
    }
}
