package io.embrace.android.embracesdk.capture.metadata;

import android.content.Context;
import android.os.Build;
import android.os.StatFs;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

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
        return statFs.getFreeBytes();
    }

    /**
     * Tries to determine whether the device is jailbroken by looking for specific directories which
     * exist on jailbroken devices. Emulators are excluded and will always return false.
     *
     * @return true if the device is jailbroken and not an emulator, false otherwise
     */
    static boolean isJailbroken() {
        if (isEmulator()) {
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
        return Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.FINGERPRINT.contains("emulator") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("sdk_gphone64") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic") ||
            "google_sdk".equals(Build.PRODUCT);
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
