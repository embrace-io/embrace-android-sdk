package io.embrace.android.embracesdk.internal.envelope.resource

import android.os.Environment
import android.os.StatFs
import android.util.DisplayMetrics
import android.view.WindowManager
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.isEmulator
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.prefs.PreferencesService
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.io.File
import java.util.Locale

internal class DeviceImpl(
    private val windowManager: WindowManager?,
    private val preferencesService: PreferencesService,
    private val backgroundWorker: BackgroundWorker,
    override val systemInfo: SystemInfo,
    private val logger: EmbLogger,
) : Device {
    override var isJailbroken: Boolean? = null
    override var screenResolution: String = ""
    private val jailbreakLocations: List<String> = listOf(
        "/sbin/",
        "/system/bin/",
        "/system/xbin/",
        "/data/local/xbin/",
        "/data/local/bin/",
        "/system/sd/xbin/",
        "/system/bin/failsafe/",
        "/data/local/"
    )

    init {
        asyncRetrieveIsJailbroken()
        asyncRetrieveScreenResolution()
    }

    private fun asyncRetrieveScreenResolution() {
        // if the screenResolution exists in memory, don't try to retrieve it
        if (screenResolution.isNotEmpty()) {
            return
        }
        backgroundWorker.submit {
            val storedScreenResolution = preferencesService.screenResolution
            // get from shared preferences
            if (storedScreenResolution != null) {
                screenResolution = storedScreenResolution
            } else {
                screenResolution = getScreenResolution(windowManager)
                preferencesService.screenResolution = screenResolution
            }
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
                displayMetrics.heightPixels
            )
        } catch (ex: Exception) {
            logger.trackInternalError(InternalErrorType.SCREEN_RES_CAPTURE_FAIL, ex)
            ""
        }
    }

    private fun asyncRetrieveIsJailbroken() {
        // if the isJailbroken property exists in memory, don't try to retrieve it
        if (isJailbroken != null) {
            return
        }
        backgroundWorker.submit {
            val storedIsJailbroken = preferencesService.jailbroken
            // load value from shared preferences
            if (storedIsJailbroken != null) {
                isJailbroken = storedIsJailbroken
            } else {
                isJailbroken = checkIfIsJailbroken()
                preferencesService.jailbroken = isJailbroken
            }
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
}
