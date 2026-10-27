package io.embrace.android.embracesdk.testframework

import android.os.Build.VERSION_CODES
import android.os.PowerManager
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowPowerManager
import java.util.concurrent.Executor

/**
 * Accepts thermal status listeners, which the default [ShadowPowerManager] rejects by throwing.
 */
@Implements(PowerManager::class)
class ThermalListenerShadowPowerManager : ShadowPowerManager() {

    @Suppress("UnusedParameter")
    @Implementation(minSdk = VERSION_CODES.Q)
    fun addThermalStatusListener(executor: Executor, listener: PowerManager.OnThermalStatusChangedListener) {
    }

    @Suppress("UnusedParameter")
    @Implementation(minSdk = VERSION_CODES.Q)
    fun removeThermalStatusListener(listener: PowerManager.OnThermalStatusChangedListener) {
    }
}
