package io.embrace.android.embracesdk.internal.anr.ndk

import android.os.Handler
import android.os.Looper
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.anr.AnrService
import io.embrace.android.embracesdk.internal.config.ConfigService
import java.util.concurrent.atomic.AtomicBoolean

class NativeThreadSamplerInstaller(
    private val sharedObjectLoader: SharedObjectLoader,
) {
    private val isMonitoring = AtomicBoolean(false)
    private var targetHandler: Handler? = null

    var currentThread: Thread? = null

    private fun prepareTargetHandler() {
        // We create a Handler here so that when the functionality is disabled locally
        // but enabled remotely, the config change callback also runs the install
        // on the target thread.
        if (Looper.myLooper() == null) {
            Looper.prepare()
        }

        val looper = Looper.myLooper()
        targetHandler = when {
            looper != null -> Handler(looper)
            else -> null
        }
        if (targetHandler == null) {
            return
        }
    }

    fun monitorCurrentThread(
        sampler: NativeThreadSamplerService,
        configService: ConfigService,
        anrService: AnrService,
    ) {
        if (isMonitoringCurrentThread()) {
            return
        } else {
            // disable monitoring since we can end up here if monitoring was enabled,
            // but the target thread has changed
            isMonitoring.set(false)
        }

        currentThread = Thread.currentThread()
        if (!sharedObjectLoader.loadEmbraceNative()) {
            return
        }
        prepareTargetHandler()

        if (configService.anrBehavior.isUnityAnrCaptureEnabled()) {
            monitorCurrentThread(sampler, anrService)
        }
    }

    private fun isMonitoringCurrentThread(): Boolean {
        return isMonitoring.get() && Thread.currentThread().id == currentThread?.id
    }

    private fun monitorCurrentThread(sampler: NativeThreadSamplerService, anrService: AnrService) {
        synchronized(this) {
            if (!isMonitoring.get()) {
                if (sampler.monitorCurrentThread()) {
                    anrService.addBlockedThreadListener(sampler)
                    isMonitoring.set(true)
                }
            }
        }
    }
}
