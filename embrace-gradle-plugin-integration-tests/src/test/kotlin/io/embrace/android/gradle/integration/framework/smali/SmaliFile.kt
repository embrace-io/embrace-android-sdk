package io.embrace.android.gradle.integration.framework.smali

import kotlinx.serialization.Serializable

@Serializable
data class SmaliFile(
    val className: String,
    val methods: List<SmaliMethod>,
)
