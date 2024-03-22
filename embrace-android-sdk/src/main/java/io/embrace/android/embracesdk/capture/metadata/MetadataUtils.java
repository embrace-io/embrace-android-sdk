package io.embrace.android.embracesdk.capture.metadata;

import android.annotation.TargetApi;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger;

/**
 * Utilities for retrieving metadata from the device's {@link Context}. This metadata is passed
 * to the API with certain messages to provide device information.
 */
final class MetadataUtils {
    private static final String OS_VERSION = "Android OS";
    private static final List<String> JAILBREAK_LOCATIONS = Arrays.asList(
        "/sbin/",
        "/system/bin/",
        "/system/xbin/",
        "/data/local/xbin/",
        "/data/local/bin/",
        "/system/sd/xbin/",
        "/system/bin/failsafe/",
        "/data/local/");


    /**
     * Gets the name of the manufacturer of the device.
     *
     * @return the name of the device manufacturer
     */
    static String getDeviceManufacturer() {
        return Build.MANUFACTURER;
    }

    /**
     * Gets the name of the model of the device.
     *
     * @return the name of the model of the device
     */
    static String getModel() {
        return Build.MODEL;
    }

    /**
     * Gets the locale of the device, represented as "language_country".
     *
     * @return the locale of the device
     */
    static String getLocale() {
        return Locale.getDefault().getLanguage() + "_" + Locale.getDefault().getCountry();
    }

    /**
     * Gets the operating system of the device. This is hard-coded to Android OS.
     *
     * @return the device's operating system
     */
    static String getOperatingSystemType() {
        return OS_VERSION;
    }

    /**
     * Gets the version of the installed operating system on the device.
     *
     * @return the version of the operating system
     */
    static String getOperatingSystemVersion() {
        return String.valueOf(Build.VERSION.RELEASE);
    }

    /**
     * Gets the version code of the running Android SDK.
     *
     * @return the running Android SDK version code
     */
    static int getOperatingSystemVersionCode() {
        return Build.VERSION.SDK_INT;
    }

    /**
     * Gets the device's screen resolution.
     *
     * @param windowManager the {@link WindowManager} from the {@link Context}
     * @return the device's screen resolution
     */
    @Nullable
    @SuppressWarnings("deprecation")
    static String getScreenResolution(WindowManager windowManager) {
        try {
            InternalStaticEmbraceLogger.logDeveloper("MetadataUtils", "Computing screen resolution");
            Display display = windowManager.getDefaultDisplay();
            DisplayMetrics displayMetrics = new DisplayMetrics();
            display.getMetrics(displayMetrics);
            return String.format(Locale.US, "%dx%d", displayMetrics.widthPixels, displayMetrics.heightPixels);
        } catch (Exception ex) {
            InternalStaticEmbraceLogger.logDebug("Could not determine screen resolution", ex);
            return null;
        }
    }

    /**
     * Gets a ID of the device's timezone, e.g. 'Europe/London'.
     *
     * @return the ID of the device's timezone
     */
    static String getTimezoneId() {
        return TimeZone.getDefault().getID();
    }

    /**
     * Gets the total storage capacity of the device.
     *
     * @param statFs the {@link StatFs} service for the device
     * @return the total storage capacity in bytes
     */
    static long getInternalStorageTotalCapacity(StatFs statFs) {
        return statFs.getTotalBytes();
    }

    /**
     * Gets the free capacity of the internal storage of the device.
     *
     * @param statFs the {@link StatFs} service for the device
     * @return the total free capacity of the internal storage of the device in bytes
     */
    static long getInternalStorageFreeCapacity(StatFs statFs) {
        InternalStaticEmbraceLogger.logDeveloper("MetadataUtils", "Getting internal storage free capacity");
        return statFs.getFreeBytes();
    }

    /**
     * Attempts to determine the disk usage of the app on the device.
     * <p>
     * If the disk usage cannot be determined, null is returned.
     *
     * @param storageStatsManager the {@link StorageStatsManager}
     * @param packageManager      the {@link PackageManager}
     * @param contextPackageName  the name of the package from the {@link Context}
     * @return optionally the disk usage of the app on the device
     */
    @TargetApi(Build.VERSION_CODES.O)
    @Nullable
    @SuppressWarnings("deprecation")
    static Long getDeviceDiskAppUsage(
        StorageStatsManager storageStatsManager,
        PackageManager packageManager,
        String contextPackageName) {
        InternalStaticEmbraceLogger.logDeveloper("MetadataUtils", "Getting device disk app usage");
        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(contextPackageName, 0);
            if (packageInfo != null && packageInfo.packageName != null) {
                StorageStats stats = storageStatsManager.queryStatsForPackage(
                    StorageManager.UUID_DEFAULT,
                    packageInfo.packageName,
                    Process.myUserHandle());
                return stats.getAppBytes() + stats.getDataBytes() + stats.getCacheBytes();
            } else {
                InternalStaticEmbraceLogger.logDeveloper("MetadataUtils", "Cannot get disk usage, packageInfo is null");
            }
        } catch (Exception ex) {
            // The package name and storage volume should always exist
            InternalStaticEmbraceLogger.logError("Error retrieving device disk usage", ex);
        }
        return null;
    }

    /**
     * Tries to determine whether the device is jailbroken by looking for specific directories which
     * exist on jailbroken devices. Emulators are excluded and will always return false.
     *
     * @return true if the device is jailbroken and not an emulator, false otherwise
     */
    static boolean isJailbroken() {
        InternalStaticEmbraceLogger.logDeveloper("MetadataUtils", "Processing jailbroken");

        if (isEmulator()) {
            InternalStaticEmbraceLogger.logDeveloper("MetadataUtils", "Device is an emulator, Jailbroken=false");
            return false;
        }

        for (String location : JAILBREAK_LOCATIONS) {
            if (new File(location + "su").exists()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tries to determine whether the device is an emulator by looking for known models and
     * manufacturers which correspond to emulators.
     *
     * @return true if the device is detected to be an emulator, false otherwise
     */
    static boolean isEmulator() {
        boolean isEmulator = Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.FINGERPRINT.contains("emulator") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("sdk_gphone64") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic") ||
            "google_sdk".equals(Build.PRODUCT);

        InternalStaticEmbraceLogger.logDeveloper("MetadataUtils", "Device is an Emulator = " + isEmulator);
        return isEmulator;
    }

    /**
     * Get the number of available cores for device info
     *
     * @return Number of cores in long
     */
    static int getNumberOfCores() {
        return Runtime.getRuntime().availableProcessors();
    }

    private MetadataUtils() {
        // Restricted constructor
    }

}
