package io.embrace.android.embracesdk.testframework

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences

/**
 * A [Context] that serves [InMemorySharedPreferences] in place of the preferences files on disk.
 */
internal class InMemoryPrefsContext(base: Context) : ContextWrapper(base) {

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        InMemorySharedPreferences.get(baseContext, name)
}
