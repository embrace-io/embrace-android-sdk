package io.embrace.android.embracesdk.capture.envelope.resource

import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.annotation.ChecksSdkIntAtLeast
import io.embrace.android.embracesdk.capture.cpu.CpuInfoDelegate
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logDebug
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logDeveloper
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
     * Gets the operating system of the device. This is hard-coded to Android OS.
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
    cpuInfoDelegate: CpuInfoDelegate
) : Device {
    override var isJailbroken: Boolean? = null
    override var screenResolution: String = ""
    private val jailbreakLocations: List<String> = mutableListOf(
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
        logDeveloper(
            "Device",
            "Precomputing values asynchronously: Jailbroken/ScreenResolution/DiskUsage"
        )
        asyncRetrieveIsJailbroken()
        asyncRetrieveScreenResolution()
    }

    private fun asyncRetrieveScreenResolution() {
        // if the screenResolution exists in memory, don't try to retrieve it
        if (screenResolution.isNotEmpty()) {
            logDeveloper("Device", "Screen resolution already exists")
            return
        }
        backgroundWorker.submit {
            logDeveloper("Device", "Async retrieve screen resolution")
            val storedScreenResolution = preferencesService.screenResolution
            // get from shared preferences
            if (storedScreenResolution != null) {
                logDeveloper("Device", "Screen resolution is present, loading from store")
                screenResolution = storedScreenResolution
            } else {
                screenResolution = getScreenResolution(windowManager)
                preferencesService.screenResolution = screenResolution
                logDeveloper("Device", "Screen resolution computed and stored")
            }
        }
    }

    private fun getScreenResolution(windowManager: WindowManager?): String {
        return try {
            logDeveloper("Device", "Computing screen resolution")
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
            logDebug("Could not determine screen resolution", ex)
            ""
        }
    }

    private fun asyncRetrieveIsJailbroken() {
        logDeveloper("Device", "Async retrieve Jailbroken")

        // if the isJailbroken property exists in memory, don't try to retrieve it
        if (isJailbroken != null) {
            logDeveloper("Device", "Jailbroken already exists")
            return
        }
        backgroundWorker.submit {
            logDeveloper("Device", "Async retrieve jailbroken")
            val storedIsJailbroken = preferencesService.jailbroken
            // load value from shared preferences
            if (storedIsJailbroken != null) {
                logDeveloper("Device", "Jailbroken is present, loading from store")
                isJailbroken = storedIsJailbroken
            } else {
                isJailbroken = checkIfIsJailbroken()
                preferencesService.jailbroken = isJailbroken
                logDeveloper("Device", "Jailbroken processed and stored")
            }
            logDeveloper("Device", "Jailbroken: $isJailbroken")
        }
    }

    /**
     * Tries to determine whether the device is jailbroken by looking for specific directories which
     * exist on jailbroken devices. Emulators are excluded and will always return false.
     *
     * @return true if the device is jailbroken and not an emulator, false otherwise
     */
    private fun checkIfIsJailbroken(): Boolean {
        logDeveloper("Device", "Processing jailbroken")
        if (isEmulator()) {
            logDeveloper("Device", "Device is an emulator, Jailbroken=false")
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
     * Tries to determine whether the device is an emulator by looking for known models and
     * manufacturers which correspond to emulators.
     *
     * @return true if the device is detected to be an emulator, false otherwise
     */
    private fun isEmulator(): Boolean {
        val isEmulator = Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.FINGERPRINT.contains("emulator") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("sdk_gphone64") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic") ||
            Build.PRODUCT.equals("google_sdk")
        logDeveloper("MetadataUtils", "Device is an Emulator = $isEmulator")
        return isEmulator
    }

    /**
     * Gets the name of the manufacturer of the device.
     *
     * @return the name of the device manufacturer
     */
    override val manufacturer: String? = Build.MANUFACTURER

    /**
     * Gets the name of the model of the device.
     *
     * @return the name of the model of the device
     */
    override val model: String? = Build.MODEL

    /**
     * Gets the operating system of the device. This is hard-coded to Android OS.
     *
     * @return the device's operating system
     */
    override val operatingSystemType: String = "Android OS"

    /**
     * Gets the version of the installed operating system on the device.
     *
     * @return the version of the operating system
     */
    override val operatingSystemVersion: String? = Build.VERSION.RELEASE

    /**
     * Gets the version code of the running Android SDK.
     *
     * @return the running Android SDK version code
     */
    @ChecksSdkIntAtLeast(parameter = 0)
    override val operatingSystemVersionCode: Int = Build.VERSION.SDK_INT

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
