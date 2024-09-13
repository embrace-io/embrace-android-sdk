package io.embrace.android.embracesdk.internal.session.lifecycle

interface StartupListener {
    /**
     * Triggered when the application has completed startup;
     */
    fun applicationStartupComplete() {}
}
