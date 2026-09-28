package io.embrace.android.gradle.integration.framework.smali

import kotlinx.serialization.Serializable

@Serializable
data class ExpectedSmaliConfig(
    val values: List<SmaliFile>,
)
