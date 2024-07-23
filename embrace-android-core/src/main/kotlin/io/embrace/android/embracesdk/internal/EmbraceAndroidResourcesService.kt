package io.embrace.android.embracesdk.internal

import android.annotation.SuppressLint
import android.content.Context

/**
 * Implementation used in production that just defers to the given [Context]
 */
public class EmbraceAndroidResourcesService(private val context: Context) :
    AndroidResourcesService {
    @SuppressLint("DiscouragedApi")
    override fun getIdentifier(name: String?, defType: String?, defPackage: String?): Int =
        context.resources.getIdentifier(name, defType, defPackage)

    override fun getString(id: Int): String = context.resources.getString(id)
}
