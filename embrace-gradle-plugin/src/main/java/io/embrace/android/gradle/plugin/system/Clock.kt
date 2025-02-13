package io.embrace.android.gradle.plugin.system

interface Clock {
    /**
     * Returns the current milliseconds from epoch.
     */
    fun now(): Long
}
