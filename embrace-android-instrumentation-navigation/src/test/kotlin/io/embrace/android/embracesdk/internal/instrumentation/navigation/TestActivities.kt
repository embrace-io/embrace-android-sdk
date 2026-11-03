package io.embrace.android.embracesdk.internal.instrumentation.navigation

import android.app.Activity

internal class DopeActivity : Activity() {
    override fun getLocalClassName(): String = "DopeActivity"
}

internal class CoolActivity : Activity() {
    override fun getLocalClassName(): String = "CoolActivity"
}
