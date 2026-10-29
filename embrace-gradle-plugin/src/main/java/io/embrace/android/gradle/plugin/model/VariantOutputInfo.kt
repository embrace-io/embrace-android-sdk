package io.embrace.android.gradle.plugin.model

import java.io.Serializable

data class VariantOutputInfo(
    val versionName: String,
    val versionCode: String,
    val packageName: String,
) : Serializable {
    private companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
