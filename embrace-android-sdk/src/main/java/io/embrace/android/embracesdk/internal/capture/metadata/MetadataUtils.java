package io.embrace.android.embracesdk.internal.capture.metadata;

import android.content.Context;
import android.os.StatFs;

import java.util.Locale;
import java.util.TimeZone;

/**
 * Utilities for retrieving metadata from the device's {@link Context}. This metadata is passed
 * to the API with certain messages to provide device information.
 */
final class MetadataUtils {
    /**
     * Gets the locale of the device, represented as "language_country".
     *
     * @return the locale of the device
     */
    static String getLocale() {
        return Locale.getDefault().getLanguage() + "_" + Locale.getDefault().getCountry();
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
