package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.capture.orientation.OrientationService
import io.embrace.android.embracesdk.payload.Orientation

internal class FakeOrientationService : FakeDataCaptureService<Orientation>(), OrientationService {

    val changes = mutableListOf<Int>()

    override fun onOrientationChanged(orientation: Int?) {
        changes.add(orientation ?: -1)
    }
}
