package io.embrace.android.gradle.integration.framework.smali

import com.squareup.moshi.JsonClass

/**
 * Represents a method in a Smali file. This contains the method signature and the return value as a string.
 */
@JsonClass(generateAdapter = true)
data class SmaliMethod(
    val signature: String,
    val returnValue: String? = null,
)
