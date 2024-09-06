package io.embrace.android.embracesdk.internal

import android.content.Context
import android.content.res.Resources

/**
 * Interface for retrieving identifiers and strings from the app's [android.content.res.Resources] object. This can be used
 * instead of directly accessing resources through the [Context] so we can more easily fake things during tests.
 */
interface AndroidResourcesService {
    fun getIdentifier(name: String?, defType: String?, defPackage: String?): Int

    @Throws(Resources.NotFoundException::class)
    fun getString(id: Int): String
}
