package io.embrace.android.gradle.integration.framework

internal class DecodedApk(
    private val stringTable: Map<String, String>
) {
    fun getStringResource(name: String): String? {
        return stringTable[name]
    }
}
