package io.embrace.android.embracesdk.internal.anr.ndk

import android.os.Handler
import android.os.Looper
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.anr.AnrService
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.payload.NativeThreadAnrSample
import java.util.concurrent.atomic.AtomicBoolean

internal class NativeThreadSamplerNdkDelegate : EmbraceNativeThreadSamplerService.NdkDelegate {
    external override fun setupNativeThreadSampler(is32Bit: Boolean): Boolean
    external override fun monitorCurrentThread(): Boolean
    external override fun startSampling(unwinderOrdinal: Int, intervalMs: Long)
    external override fun finishSampling(): List<NativeThreadAnrSample>?
}

internal class NativeThreadSamplerInstaller(
    private val sharedObjectLoader: SharedObjectLoader,
    private val logger: EmbLogger,
) {
    private val isMonitoring = AtomicBoolean(false)
    private var targetHandler: Handler? = null

    internal var currentThread: Thread? = null

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
            logger.logError(
                "Native thread sampler init failed: Failed to create Handler for target native thread"
            )
            return
        }
    }

    fun monitorCurrentThread(
        sampler: NativeThreadSamplerService,
        configService: ConfigService,
        anrService: AnrService
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

        if (configService.anrBehavior.isNativeThreadAnrSamplingEnabled()) {
            monitorCurrentThread(sampler, anrService)
        }

        // always install the handler. if config subsequently changes we take the decision
        // to just ignore anr intervals, rather than attempting to uninstall the handler
        configService.addListener {
            onConfigChange(configService, sampler, anrService)
        }
    }

    private fun isMonitoringCurrentThread(): Boolean {
        return isMonitoring.get() && Thread.currentThread().id == currentThread?.id
    }

    private fun onConfigChange(
        configService: ConfigService,
        sampler: NativeThreadSamplerService,
        anrService: AnrService
    ) {
        targetHandler?.post(
            Runnable {
                if (configService.anrBehavior.isNativeThreadAnrSamplingEnabled() && !isMonitoring.get()) {
                    monitorCurrentThread(sampler, anrService)
                }
            }
        )
    }

    private fun monitorCurrentThread(sampler: NativeThreadSamplerService, anrService: AnrService) {
        synchronized(this) {
            if (!isMonitoring.get()) {
                logger.logInfo("Installing native sampling on '${Thread.currentThread().name}'")
                if (sampler.monitorCurrentThread()) {
                    anrService.addBlockedThreadListener(sampler)
                    isMonitoring.set(true)
                }
            }
        }
    }
}
