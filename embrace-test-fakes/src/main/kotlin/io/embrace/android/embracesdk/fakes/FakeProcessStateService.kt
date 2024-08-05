package io.embrace.android.embracesdk.fakes

import android.content.res.Configuration
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateListener
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateService

public class FakeProcessStateService(
    override var isInBackground: Boolean = false,
) : ProcessStateService {

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
    }

    public val listeners: MutableList<ProcessStateListener> = mutableListOf()
    public var config: Configuration? = null

    override fun addListener(listener: ProcessStateListener) {
        listeners.add(listener)
    }

    override fun close() {
    }

    override fun onForeground() {
    }

    override fun onBackground() {
    }

    override fun getAppState(): String = when (isInBackground) {
        true -> "background"
        false -> "foreground"
    }
}
