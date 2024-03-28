package io.embrace.android.embracesdk.capture.orientation

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.payload.Orientation
import java.util.LinkedList

internal class EmbraceOrientationService(
    private val clock: Clock
) : OrientationService {

    /**
     * States the activity orientations.
     */
    private val orientations = LinkedList<Orientation>()

    override fun onOrientationChanged(orientation: Int?) {
        if (orientation != null && (orientations.isEmpty() || orientations.last.internalOrientation != orientation)) {
            orientations.add(Orientation(orientation, clock.now()))
        }
    }

    override fun getCapturedData(): List<Orientation> = orientations

    override fun cleanCollections() = orientations.clear()
}
