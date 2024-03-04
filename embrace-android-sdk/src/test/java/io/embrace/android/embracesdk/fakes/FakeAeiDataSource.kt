package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.arch.destination.LogWriter
import io.embrace.android.embracesdk.capture.aei.AeiDataSource
import io.embrace.android.embracesdk.payload.AppExitInfoData

internal class FakeAeiDataSource : AeiDataSource {

    var data: List<AppExitInfoData> =
        listOf(AppExitInfoData(null, null, null, null, null, null, null, null, null, null, null))

    override fun alterSessionSpan(
        inputValidation: () -> Boolean,
        captureAction: LogWriter.() -> Unit
    ): Boolean {
        return false
    }

    override fun enableDataCapture() {
    }

    override fun disableDataCapture() {
    }

    override fun resetDataCaptureLimits() {
    }
}
