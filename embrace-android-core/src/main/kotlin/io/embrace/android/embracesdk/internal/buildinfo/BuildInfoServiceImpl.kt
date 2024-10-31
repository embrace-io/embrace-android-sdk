package io.embrace.android.embracesdk.internal.buildinfo

import android.content.res.Resources
import io.embrace.android.embracesdk.internal.AndroidResourcesService
import io.embrace.android.embracesdk.internal.config.instrumented.ProjectConfig

internal class BuildInfoServiceImpl(
    resources: AndroidResourcesService,
    packageName: String,
) : BuildInfoService {

    private val info by lazy {
        fromResources(resources, packageName)
    }

    override fun getBuildInfo(): BuildInfo = info

    companion object {
        private const val BUILD_INFO_RN_BUNDLE_ID: String = "emb_rn_bundle_id"
        private const val RES_TYPE_STRING = "string"

        /**
         * Loads the build information from resources provided by the config file packaged within the application by Gradle at
         * build-time.
         *
         * @return the build information
         */
        @JvmStatic
        fun fromResources(resources: AndroidResourcesService, packageName: String): BuildInfo {
            val buildInfo = BuildInfo(
                ProjectConfig.getBuildId(),
                ProjectConfig.getBuildType(),
                ProjectConfig.getBuildFlavor(),
                getBuildResource(resources, packageName, BUILD_INFO_RN_BUNDLE_ID),
            )
            return buildInfo
        }

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
}
