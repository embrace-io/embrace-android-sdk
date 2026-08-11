package io.embrace.android.embracesdk.internal.envelope.resource

import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.DisplayMetrics
import android.view.WindowManager
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.isEmulator
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.io.File
import java.util.Locale

class DeviceImpl(
    private val windowManagerProvider: Provider<WindowManager?>,
    private val backgroundWorker: BackgroundWorker,
    override val systemInfo: SystemInfo,
    private val logger: InternalLogger,
) : Device {
    override var isJailbroken: Boolean? = false
    override var screenResolution: String = ""
    override var usesEmmcStorage: Boolean? = null

    private val jailbreakLocations: List<String> = listOf(
        "/sbin/",
        "/system/bin/",
        "/system/xbin/",
        "/data/local/xbin/",
        "/data/local/bin/",
        "/system/sd/xbin/",
        "/system/bin/failsafe/",
        "/data/local/",
    )

    init {
        asyncRetrieveIsJailbroken()
        asyncRetrieveScreenResolution()
        asyncRetrieveUsesEmmcStorage()
    }

    private fun asyncRetrieveUsesEmmcStorage() {
        backgroundWorker.submit {
            usesEmmcStorage = detectUsesEmmcStorage()
        }
    }

    private fun asyncRetrieveScreenResolution() {
        backgroundWorker.submit {
            screenResolution = getScreenResolution(windowManagerProvider())
        }
    }

    @Suppress("DEPRECATION")
    private fun getScreenResolution(windowManager: WindowManager?): String {
        return try {
            val display = windowManager?.defaultDisplay
            val displayMetrics = DisplayMetrics()
            display?.getMetrics(displayMetrics)
            String.format(
                Locale.US,
                "%dx%d",
                displayMetrics.widthPixels,
                displayMetrics.heightPixels,
            )
        } catch (ex: Exception) {
            logger.trackInternalError(InternalErrorType.ScreenResCaptureFail, ex)
            ""
        }
    }

    private fun asyncRetrieveIsJailbroken() {
        backgroundWorker.submit {
            isJailbroken = checkIfIsJailbroken()
        }
    }

    /**
     * Tries to determine whether the device is jailbroken by looking for specific directories which
     * exist on jailbroken devices. Emulators are excluded and will always return false.
     *
     * @return true if the device is jailbroken and not an emulator, false otherwise
     */
    private fun checkIfIsJailbroken(): Boolean {
        if (systemInfo.isEmulator()) {
            return false
        }
        for (location in jailbreakLocations) {
            if (File(location + "su").exists()) {
                return true
            }
        }
        return false
    }

    /**
     * Get the number of available cores for device info
     *
     * @return Number of cores in long
     */
    override val numberOfCores: Int = Runtime.getRuntime().availableProcessors()

    /**
     * Gets the free capacity of the internal storage of the device.
     *
     * @param statFs the {@link StatFs} service for the device
     * @return the total free capacity of the internal storage of the device in bytes
     */
    override val internalStorageTotalCapacity: Lazy<Long> =
        lazy { StatFs(Environment.getDataDirectory().path).totalBytes }

    override val socModel: String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.trim().takeIf { it.isNotEmpty() && !it.equals(Build.UNKNOWN, ignoreCase = true) }
        } else {
            null
        }
}
