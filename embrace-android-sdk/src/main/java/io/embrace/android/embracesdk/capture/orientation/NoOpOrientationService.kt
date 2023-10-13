package io.embrace.android.embracesdk.capture.orientation

import io.embrace.android.embracesdk.payload.Orientation

internal class NoOpOrientationService : OrientationService {
    override fun onOrientationChanged(orientation: Int?) {
        return
    }

    override fun cleanCollections() {
    }

    override fun getCapturedData(): List<Orientation> {
        return emptyList()
    }
}
