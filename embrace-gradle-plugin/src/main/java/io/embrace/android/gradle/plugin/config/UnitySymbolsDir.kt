package io.embrace.android.gradle.plugin.config

import java.io.File
import java.io.Serializable

/**
 * Holds the directory where symbols files are located.
 * It also indicates if symbols are in a zip file or not.
 */
data class UnitySymbolsDir(
    val unitySymbolsDir: File? = null,
    val zippedSymbols: Boolean = false
) : Serializable {

    private companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }

    fun isDirPresent() = unitySymbolsDir != null
}
