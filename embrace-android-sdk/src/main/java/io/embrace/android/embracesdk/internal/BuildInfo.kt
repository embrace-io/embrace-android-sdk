package io.embrace.android.embracesdk.internal

import android.content.res.Resources

/**
 * Specifies the application ID and build ID.
 */
internal class BuildInfo internal constructor(
    /**
     * The ID of the particular build, generated at compile-time.
     */
    val buildId: String?,

    /**
     * The BuildType name of the particular build, extracted at compile-time.
     */
    val buildType: String?,

    /**
     * The Flavor name of the particular build, extracted at compile-time.
     */
    val buildFlavor: String?
) {

    internal companion object {
        const val BUILD_INFO_BUILD_ID: String = "emb_build_id"
        const val BUILD_INFO_BUILD_TYPE: String = "emb_build_type"
        const val BUILD_INFO_BUILD_FLAVOR: String = "emb_build_flavor"
        private const val RES_TYPE_STRING = "string"

        /**
         * Loads the build information from resources provided by the config file packaged within the application by Gradle at
         * build-time.
         *
         * @return the build information
         */
        @JvmStatic
        fun fromResources(resources: AndroidResourcesService, packageName: String): BuildInfo {
            return BuildInfo(
                getBuildResource(resources, packageName, BUILD_INFO_BUILD_ID),
                getBuildResource(resources, packageName, BUILD_INFO_BUILD_TYPE),
                getBuildResource(resources, packageName, BUILD_INFO_BUILD_FLAVOR)
            )
        }

        /**
         * Given a build property name and a build property type, retrieves the embrace build resource value.
         */
        fun getBuildResource(
            resources: AndroidResourcesService,
            packageName: String,
            buildProperty: String
        ): String? {
            return try {
                val resourceId = resources.getIdentifier(buildProperty, RES_TYPE_STRING, packageName)

                // Flavor value is optional, so we should not hard fail if doesn't exists.
                if (buildProperty == BUILD_INFO_BUILD_FLAVOR && resourceId == 0) {
                    null
                } else resources.getString(resourceId)
            } catch (ex: NullPointerException) {
                throw IllegalArgumentException(
                    "No resource found for $buildProperty property. Failed to create build info.",
                    ex
                )
            } catch (ex: Resources.NotFoundException) {
                throw IllegalArgumentException(
                    "No resource found for $buildProperty property. Failed to create build info.",
                    ex
                )
            }
        }
    }
}
