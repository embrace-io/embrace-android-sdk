package io.embrace.android.embracesdk.internal.instrumentation.powersave

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter

internal class FakeApplication : Application() {

    val receivers = mutableListOf<BroadcastReceiver>()

    override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent? {
        receivers.add(checkNotNull(receiver))
        return null
    }

    override fun unregisterReceiver(receiver: BroadcastReceiver?) {
        receivers.remove(receiver)
    }
}
