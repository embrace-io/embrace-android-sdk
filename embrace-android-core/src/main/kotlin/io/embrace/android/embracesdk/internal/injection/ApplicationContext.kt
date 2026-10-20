package io.embrace.android.embracesdk.internal.injection

import android.app.Application
import android.content.Context

fun Context.applicationContextOrSelf(): Context = when (this) {
    is Application -> this
    else -> applicationContext
}
