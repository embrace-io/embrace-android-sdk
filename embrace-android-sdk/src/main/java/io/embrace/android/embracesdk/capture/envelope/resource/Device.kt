package io.embrace.android.embracesdk.capture.envelope.resource

import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.annotation.ChecksSdkIntAtLeast
import io.embrace.android.embracesdk.capture.cpu.CpuInfoDelegate
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.prefs.PreferencesService
import io.embrace.android.embracesdk.worker.BackgroundWorker
import java.io.File
import java.util.Locale

internal interface Device {

    /**
     * Tries to determine whether the device is jailbroken by looking for specific directories which
     * exist on jailbroken devices. Emulators are excluded and will always return false.
     *
     * @return true if the device is jailbroken and not an emulator, false otherwise
     */
    var isJailbroken: Boolean?

    /**
     * Gets the device's screen resolution.
     *
     * @param windowManager the {@link WindowManager} from the {@link Context}
     * @return the device's screen resolution
     */
    var screenResolution: String

    /**
     * Gets the name of the manufacturer of the device.
     *
     * @return the name of the device manufacturer
     */
    val manufacturer: String?

    /**
     * Gets the name of the model of the device.
     *
     * @return the name of the model of the device
     */
    val model: String?

    /**
     * Gets the operating system of the device. This is hard-coded to 'android'.
     *
     * @return the device's operating system
     */
    val operatingSystemType: String

    /**
     * Gets the version of the installed operating system on the device.
     *
     * @return the version of the operating system
     */
    val operatingSystemVersion: String?

    /**
     * Gets the version code of the running Android SDK.
     *
     * @return the running Android SDK version code
     */
    val operatingSystemVersionCode: Int

    /**
     * Get the number of available cores for device info
     *
     * @return Number of cores in long
     */
    val numberOfCores: Int

    /**
     * Gets the free capacity of the internal storage of the device.
     *
     * @param statFs the {@link StatFs} service for the device
     * @return the total free capacity of the internal storage of the device in bytes
     */
    val internalStorageTotalCapacity: Lazy<Long>

    /**
     * The name of the primary CPU of the device, obtained with the system call 'ro.board.platform'.
     *
     * @return the name of the primary CPU of the device
     */
    val cpuName: String?

    /**
     * The EGL (Embedded-System Graphics Library) information obtained with the system call 'ro.hardware.egl'
     *
     * @return the ELG of the primary CPU of the device
     */
    val eglInfo: String?
}

internal class DeviceImpl(
    private val windowManager: WindowManager?,
    private val preferencesService: PreferencesService,
    private val backgroundWorker: BackgroundWorker,
    private val systemInfo: SystemInfo,
    cpuInfoDelegate: CpuInfoDelegate,
    private val logger: InternalEmbraceLogger
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
            logger.logDebug("Could not determine screen resolution", ex)
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
        if (isEmulator(systemInfo)) {
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
     * Gets the name of the manufacturer of the device.
     *
     * @return the name of the device manufacturer
     */
    override val manufacturer: String = systemInfo.deviceManufacturer

    /**
     * Gets the name of the model of the device.
     *
     * @return the name of the model of the device
     */
    override val model: String = systemInfo.deviceModel

    /**
     * Gets the operating system of the device. This is hard-coded to 'android'.
     *
     * @return the device's operating system
     */
    override val operatingSystemType: String = systemInfo.osName

    /**
     * Gets the version of the installed operating system on the device.
     *
     * @return the version of the operating system
     */
    override val operatingSystemVersion: String = systemInfo.osVersion

    /**
     * Gets the version code of the running Android SDK.
     *
     * @return the running Android SDK version code
     */
    @ChecksSdkIntAtLeast(parameter = 0)
    override val operatingSystemVersionCode: Int = systemInfo.androidOsApiLevel.toInt()

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
    override val internalStorageTotalCapacity = lazy { StatFs(Environment.getDataDirectory().path).totalBytes }

    override val cpuName: String? = cpuInfoDelegate.getCpuName()

    override val eglInfo: String? = cpuInfoDelegate.getEgl()
}

/**
 * Tries to determine whether the device is an emulator by looking for known models and
 * manufacturers which correspond to emulators.
 *
 * @return true if the device is detected to be an emulator, false otherwise
 */
internal fun isEmulator(systemInfo: SystemInfo): Boolean =
    Build.FINGERPRINT.startsWith("generic") ||
        Build.FINGERPRINT.startsWith("unknown") ||
        Build.FINGERPRINT.contains("emulator") ||
        systemInfo.deviceModel.contains("google_sdk") ||
        systemInfo.deviceModel.contains("sdk_gphone64") ||
        systemInfo.deviceModel.contains("Emulator") ||
        systemInfo.deviceModel.contains("Android SDK built for") ||
        systemInfo.deviceManufacturer.contains("Genymotion") ||
        Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic") ||
        Build.PRODUCT.equals("google_sdk")
