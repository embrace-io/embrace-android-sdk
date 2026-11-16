package io.embrace.android.embracesdk.internal.instrumentation.view

import android.app.Activity
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.SpanToken
import io.embrace.android.embracesdk.internal.arch.lifecycle.ActivityLifecycle
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.utils.event.EventHandler

/**
 * Captures fragment views.
 */
class ViewDataSource(
    private val args: InstrumentationArgs,
) : DataSourceImpl(
    args,
    UpToLimitStrategy { args.configService.config.breadcrumb.fragmentLimit },
    "view_data_source",
) {

    private val viewSpans: LinkedHashMap<String, SpanToken> = LinkedHashMap()

    internal val trackedViewCount: Int
        get() = synchronized(viewSpans) { viewSpans.size }

    private val onStarted = EventHandler<Activity> { activity ->
        errorHandler.guardSdkCode(SdkErrorType.UiCallbackFail) {
            changeView(activity.javaClass.name)
        }
    }

    /**
     * Closes all open fragments when the activity closes.
     */
    private val onStopped = EventHandler<Activity> {
        errorHandler.guardSdkCode(SdkErrorType.UiCallbackFail, ::onViewClose)
    }

    override fun onDataCaptureEnabled() {
        args.eventBus.addHandler(ActivityLifecycle.STARTED, onStarted)
        args.eventBus.addHandler(ActivityLifecycle.STOPPED, onStopped)
    }

    override fun onDataCaptureDisabled() {
        args.eventBus.removeHandler(ActivityLifecycle.STARTED, onStarted)
        args.eventBus.removeHandler(ActivityLifecycle.STOPPED, onStopped)
    }

    /**
     * Called when a view is started. If a view with the same name is already running, it will be ended.
     */
    fun startView(name: String?, isManual: Boolean = false): Boolean {
        captureTelemetry(inputValidation = { !name.isNullOrEmpty() }) {
            synchronized(viewSpans) {
                // Remove previous entry even if we don't replace it, like if we can't start a new view span because of limits
                viewSpans.remove(name)?.stop()
                startSpanCapture(SchemaType.View(checkNotNull(name), isManual), clock.now()).apply {
                    viewSpans[name] = this
                }
            }
        }
        return true
    }

    /**
     * Called when a view is started, ending the last view.
     */
    fun changeView(name: String?) {
        synchronized(viewSpans) {
            val lastView = viewSpans.keys.lastOrNull()
            endView(lastView)
            startView(name)
        }
    }

    /**
     * Called when a view is ended.
     */
    fun endView(name: String?): Boolean {
        if (name.isNullOrEmpty()) {
            return false
        }
        synchronized(viewSpans) {
            viewSpans.remove(name)?.stop()
        }
        return true
    }

    /**
     * Called when the activity is closed (and therefore all views are assumed to close).
     */
    private fun onViewClose() {
        synchronized(viewSpans) {
            viewSpans.values.forEach { it.stop() }
            viewSpans.clear()
        }
    }
}
