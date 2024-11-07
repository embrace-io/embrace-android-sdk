package io.embrace.android.embracesdk.internal.buildinfo

import android.content.res.Resources
import io.embrace.android.embracesdk.internal.AndroidResourcesService
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig

internal class BuildInfoServiceImpl(
    private val instrumentedConfig: InstrumentedConfig,
    private val resources: AndroidResourcesService,
    private val packageName: String,
) : BuildInfoService {

    companion object {
        private const val BUILD_INFO_RN_BUNDLE_ID: String = "emb_rn_bundle_id"
        private const val RES_TYPE_STRING = "string"
    }

    private val info by lazy {
        BuildInfo(
            instrumentedConfig.project.getBuildId(),
            instrumentedConfig.project.getBuildType(),
            instrumentedConfig.project.getBuildFlavor(),
            getBuildResource(resources, packageName, BUILD_INFO_RN_BUNDLE_ID),
        )
    }

    override fun getBuildInfo(): BuildInfo = info

    /**
     * Given a build property name and a build property type, retrieves the embrace build resource value.
     */
    fun getBuildResource(
        resources: AndroidResourcesService,
        packageName: String,
        buildProperty: String,
    ): String? {
        return try {
            val resourceId =
                resources.getIdentifier(buildProperty, RES_TYPE_STRING, packageName)
            resources.getString(resourceId)
        } catch (ex: NullPointerException) {
            throw IllegalArgumentException(
                "No resource found for $buildProperty property. Failed to create build info.",
                ex
            )
        } catch (ex: Resources.NotFoundException) {
            null
        }
    }
}
