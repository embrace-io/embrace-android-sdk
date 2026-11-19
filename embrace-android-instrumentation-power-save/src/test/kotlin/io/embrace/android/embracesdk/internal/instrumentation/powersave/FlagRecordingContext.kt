package io.embrace.android.embracesdk.internal.instrumentation.powersave

import android.content.BroadcastReceiver
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter

internal class FlagRecordingContext : ContextWrapper(null) {

    val flags = mutableListOf<Int?>()

    override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent? {
        flags.add(null)
        return null
    }

    override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, flags: Int): Intent? {
        this.flags.add(flags)
        return null
    }
}
