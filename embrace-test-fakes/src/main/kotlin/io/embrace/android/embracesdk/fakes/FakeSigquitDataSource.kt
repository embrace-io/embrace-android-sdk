package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.anr.sigquit.SigquitDataSource
import io.embrace.android.embracesdk.internal.arch.destination.SessionSpanWriter

public class FakeSigquitDataSource : SigquitDataSource {

    override fun saveSigquit(timestamp: Long) {
    }

    override fun captureData(
        inputValidation: () -> Boolean,
        captureAction: SessionSpanWriter.() -> Unit
    ): Boolean {
        return true
    }

    override fun enableDataCapture() {
    }

    override fun disableDataCapture() {
    }

    override fun resetDataCaptureLimits() {
    }
}
