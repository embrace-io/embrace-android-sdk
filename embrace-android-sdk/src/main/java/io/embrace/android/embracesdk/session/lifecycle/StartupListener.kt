package io.embrace.android.embracesdk.session.lifecycle

internal interface StartupListener {
    /**
     * Triggered when the application has completed startup;
     */
    fun applicationStartupComplete() {}
}
