package io.embrace.android.embracesdk.fakes

import android.content.res.Configuration
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateListener
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateService

internal class FakeProcessStateService(
    override var isInBackground: Boolean = false,
) : ProcessStateService {

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
    }

    val listeners: MutableList<ProcessStateListener> = mutableListOf()
    var config: Configuration? = null

    override fun addListener(listener: ProcessStateListener) {
        listeners.add(listener)
    }

    override fun close() {
    }

    override fun onForeground() {
    }

    override fun onBackground() {
    }
}
