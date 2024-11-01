package io.embrace.android.embracesdk.internal

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Component to load the Embrace native binary
 */
interface SharedObjectLoader {
    val loaded: AtomicBoolean

    /**
     * Load Embrace native binary if necessary
     */
    fun loadEmbraceNative(): Boolean
}
