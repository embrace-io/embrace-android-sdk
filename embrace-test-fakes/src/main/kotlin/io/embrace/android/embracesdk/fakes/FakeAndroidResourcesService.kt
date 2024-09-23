package io.embrace.android.embracesdk.fakes

import android.content.res.Resources
import io.embrace.android.embracesdk.internal.AndroidResourcesService

/**
 * Fake [AndroidResourcesService] loaded with gradle-populated resources from the build that the SDK expects.
 * New identifiers and associated strings can be added by directly accessing [resourceValues], but the identifiers returned from
 * [getIdentifier] are fixed to the few that the SDK expects
 */
class FakeAndroidResourcesService : AndroidResourcesService {

    override fun getIdentifier(name: String?, defType: String?, defPackage: String?): Int = 0

    override fun getString(id: Int): String = throw Resources.NotFoundException()
}
