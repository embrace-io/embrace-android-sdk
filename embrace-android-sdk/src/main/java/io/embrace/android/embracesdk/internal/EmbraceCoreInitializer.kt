package io.embrace.android.embracesdk.internal

import android.content.Context
import androidx.startup.Initializer
import io.embrace.android.embracesdk.Embrace

internal class EmbraceCoreInitializer : Initializer<Embrace> {

    override fun create(context: Context): Embrace {
        val embrace = Embrace.getInstance()
        embrace.start(context)
        return embrace
    }

    override fun dependencies() = emptyList<Class<out Initializer<*>>>()
}
