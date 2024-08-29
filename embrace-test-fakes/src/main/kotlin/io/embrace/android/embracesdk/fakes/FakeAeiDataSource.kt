package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.destination.LogWriter
import io.embrace.android.embracesdk.internal.capture.aei.AeiDataSource
import io.embrace.android.embracesdk.internal.payload.AppExitInfoData

public class FakeAeiDataSource : AeiDataSource {

    public var data: List<AppExitInfoData> =
        listOf(AppExitInfoData(null, null, null, null, null, null, null, null, null, null, null))

    override fun captureData(
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
