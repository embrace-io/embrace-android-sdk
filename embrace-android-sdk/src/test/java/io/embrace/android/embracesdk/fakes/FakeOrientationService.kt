package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.capture.orientation.OrientationService
import io.embrace.android.embracesdk.payload.Orientation

internal class FakeOrientationService : FakeDataCaptureService<Orientation>(), OrientationService {

    override fun onOrientationChanged(orientation: Int?) {
    }
}
