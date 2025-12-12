package io.embrace.android.gradle.plugin.model

import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantOutput
import com.android.build.api.variant.VariantOutputConfiguration
import org.gradle.api.Project
import org.gradle.api.provider.Provider
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

private const val UNKNOWN = "UNKNOWN"

/**
 * Creates a Provider of VariantOutputInfo from a Variant by selecting the main output.
 *
 * Selection priority:
 * 1. SINGLE - for non-split builds
 * 2. UNIVERSAL - for split builds (contains base version code)
 * 3. First output - defensive fallback
 *
 * For non-application variants or when no output is found, returns a provider with UNKNOWN values.
 */
fun Variant.toVariantOutputInfoProvider(project: Project): Provider<VariantOutputInfo> {
    val unknownProvider = project.provider {
        VariantOutputInfo(
            versionName = UNKNOWN,
            versionCode = UNKNOWN,
            packageName = UNKNOWN
        )
    }

    val appVariant = this as? ApplicationVariant ?: return unknownProvider
    val mainOutput = appVariant.getMainOutput() ?: return unknownProvider

    val versionName = mainOutput.versionName.map { it ?: UNKNOWN }
    val versionCode = mainOutput.versionCode.map { it?.toString() ?: UNKNOWN }
    val packageName = appVariant.applicationId.map { it ?: UNKNOWN }

    return versionName
        .zip(versionCode) { name, code -> name to code }
        .zip(packageName) { versionPair, pkg ->
            VariantOutputInfo(
                versionName = versionPair.first,
                versionCode = versionPair.second,
                packageName = pkg
            )
        }
}

private fun ApplicationVariant.getMainOutput(): VariantOutput? {
    return outputs.firstOrNull {
        it.outputType == VariantOutputConfiguration.OutputType.SINGLE
    } ?: outputs.firstOrNull {
        it.outputType == VariantOutputConfiguration.OutputType.UNIVERSAL
    } ?: outputs.firstOrNull()
}
