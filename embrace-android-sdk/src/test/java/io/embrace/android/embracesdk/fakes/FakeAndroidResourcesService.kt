package io.embrace.android.embracesdk.fakes

import android.content.res.Resources
import io.embrace.android.embracesdk.config.local.LocalConfig
import io.embrace.android.embracesdk.internal.AndroidResourcesService
import io.embrace.android.embracesdk.internal.BuildInfo

/**
 * Fake [AndroidResourcesService] loaded with gradle-populated resources from the build that the SDK expects.
 * New identifiers and associated strings can be added by directly accessing [resourceValues], but the identifiers returned from
 * [getIdentifier] are fixed to the few that the SDK expects
 */
internal class FakeAndroidResourcesService : AndroidResourcesService {
    val resourceValues = sdkResources.associate { it.second to it.third }.toMutableMap()
    private val identifiers = sdkResources.associate { it.first to it.second }

    override fun getIdentifier(name: String?, defType: String?, defPackage: String?): Int = identifiers[requireNotNull(name)] ?: 0

    override fun getString(id: Int): String = resourceValues[id] ?: throw Resources.NotFoundException()

    companion object {
        private val sdkResources = listOf(
            Triple(BuildInfo.BUILD_INFO_BUILD_ID, 9991, "5.22.0"),
            Triple(BuildInfo.BUILD_INFO_BUILD_TYPE, 9992, "release"),
            Triple(BuildInfo.BUILD_INFO_BUILD_FLAVOR, 9993, "delicious"),
            Triple(LocalConfig.BUILD_INFO_APP_ID, 9994, "true"),
            Triple(LocalConfig.BUILD_INFO_NDK_ENABLED, 9995, "true"),
            Triple(LocalConfig.NDK_ENABLED_DEFAULT.toString(), 9996, "true")
        )
    }
}
