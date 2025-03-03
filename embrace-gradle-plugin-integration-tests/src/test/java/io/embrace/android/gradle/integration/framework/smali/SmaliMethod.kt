package io.embrace.android.gradle.integration.framework.smali

/**
 * Represents a method in a Smali file. This contains the method signature and the return value as a string.
 */
data class SmaliMethod(
    val signature: String,
    val returnValue: String,
)
