package io.embrace.android.embracesdk.internal.instrumentation.view

import android.app.Activity
import android.app.Application
import android.os.Bundle
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.SpanToken
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType

/**
 * Captures fragment views.
 */
class ViewDataSource(
    private val args: InstrumentationArgs,
) : DataSourceImpl(
    args,
    UpToLimitStrategy { args.configService.breadcrumbBehavior.getFragmentBreadcrumbLimit() },
    "view_data_source"
),
    Application.ActivityLifecycleCallbacks {

    private val application: Application = args.application

    private val viewSpans: LinkedHashMap<String, SpanToken> = LinkedHashMap()

    override fun onDataCaptureEnabled() {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onDataCaptureDisabled() {
        application.unregisterActivityLifecycleCallbacks(this)
    }

    /**
     * Called when a view is started. If a view with the same name is already running, it will be ended.
     */
    fun startView(name: String?): Boolean {
        captureTelemetry(inputValidation = { !name.isNullOrEmpty() }) {
            viewSpans[name]?.stop() // End the last view if it exists.

            startSpanCapture(SchemaType.View(checkNotNull(name)), clock.now())?.apply {
                viewSpans[name] = this
            }
        }
        return true
    }

    /**
     * Called when a view is started, ending the last view.
     */
    fun changeView(name: String?) {
        val lastView = viewSpans.keys.lastOrNull()
        endView(lastView)
        startView(name)
    }

    /**
     * Called when a view is ended.
     */
    fun endView(name: String?): Boolean {
        if (name.isNullOrEmpty()) {
            return false
        }
        viewSpans.remove(name)?.stop()
        return true
    }

    /**
     * Called when the activity is closed (and therefore all views are assumed to close).
     */
    fun onViewClose() {
        viewSpans.forEach { (_, span) ->
            span.stop()
        }
    }

    override fun onActivityStarted(activity: Activity) {
        changeView(activity.javaClass.name)
    }

    /**
     * Close all open fragments when the activity closes
     */
    override fun onActivityStopped(activity: Activity) {
        onViewClose()
    }

    override fun onActivityCreated(p0: Activity, p1: Bundle?) {
    }

    override fun onActivityDestroyed(p0: Activity) {
    }

    override fun onActivityPaused(p0: Activity) {
    }

    override fun onActivityResumed(p0: Activity) {
    }

    override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {
    }
}
