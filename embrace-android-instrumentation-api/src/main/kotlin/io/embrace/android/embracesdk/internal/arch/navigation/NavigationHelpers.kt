package io.embrace.android.embracesdk.internal.arch.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Get an ID that is unique for a given Activity instance.
 */
fun Activity.getId(): Int = System.identityHashCode(this)

/**
 * Find an Activity attached to the given Context or ContextWrapper that wraps it.
 */
fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) {
            return current
        }
        current = current.baseContext
    }
    return null
}
