package io.embrace.android.embracesdk.fakes

import android.content.res.Resources
import io.embrace.android.embracesdk.internal.AndroidResourcesService
import io.embrace.android.embracesdk.internal.BuildInfo

/**
 * Fake [AndroidResourcesService] loaded with gradle-populated resources from the build that the SDK expects.
 * New identifiers and associated strings can be added by directly accessing [resourceValues], but the identifiers returned from
 * [getIdentifier] are fixed to the few that the SDK expects
 */
public class FakeAndroidResourcesService : AndroidResourcesService {
    private val resourceValues: MutableMap<Int, String> = sdkResources.associate { it.second to it.third }.toMutableMap()
    private val identifiers = sdkResources.associate { it.first to it.second }

    override fun getIdentifier(name: String?, defType: String?, defPackage: String?): Int = identifiers[requireNotNull(name)] ?: 0

    override fun getString(id: Int): String = resourceValues[id] ?: throw Resources.NotFoundException()

    private companion object {
        private val sdkResources = listOf(
            Triple(BuildInfo.BUILD_INFO_BUILD_ID, 9991, "5.22.0"),
            Triple(BuildInfo.BUILD_INFO_BUILD_TYPE, 9992, "release"),
            Triple(BuildInfo.BUILD_INFO_BUILD_FLAVOR, 9993, "delicious"),
            Triple("emb_app_id", 9994, "true"),
            Triple("emb_ndk_enabled", 9995, "true"),
            Triple("false", 9996, "true")
        )
    }
}
