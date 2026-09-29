package io.embrace.android.gradle.integration.framework.smali

import kotlinx.serialization.Serializable

/**
 * Represents a method in a Smali file. This contains the method signature and the return value as a string.
 */
@Serializable
data class SmaliMethod(
    val signature: String,
    val returnValue: String? = null,
    val embraceHook: String? = null,
)
