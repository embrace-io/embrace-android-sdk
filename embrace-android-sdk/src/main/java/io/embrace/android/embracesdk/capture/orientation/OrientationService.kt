package io.embrace.android.embracesdk.capture.orientation

import io.embrace.android.embracesdk.arch.DataCaptureService
import io.embrace.android.embracesdk.payload.Orientation

internal interface OrientationService : DataCaptureService<List<Orientation>?> {
    fun onOrientationChanged(orientation: Int?)
}
