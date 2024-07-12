package io.embrace.android.embracesdk.capture.crumbs

import io.embrace.android.embracesdk.config.behavior.BreadcrumbBehavior
import io.embrace.android.embracesdk.internal.arch.datasource.NoInputValidation
import io.embrace.android.embracesdk.internal.arch.datasource.SpanDataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.startSpanCapture
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.spans.EmbraceSpan

/**
 * Captures fragment views.
 */
internal class ViewDataSource(
    breadcrumbBehavior: BreadcrumbBehavior,
    private val clock: Clock,
    spanService: SpanService,
    logger: EmbLogger
) : SpanDataSourceImpl(
    spanService,
    logger,
    UpToLimitStrategy { breadcrumbBehavior.getFragmentBreadcrumbLimit() }
) {

    private val viewSpans: LinkedHashMap<String, EmbraceSpan> = LinkedHashMap()

    /**
     * Called when a view is started. If a view with the same name is already running, it will be ended.
     */
    fun startView(name: String?): Boolean = captureSpanData(
        countsTowardsLimits = true,
        inputValidation = { !name.isNullOrEmpty() },
        captureAction = {
            viewSpans[name]?.stop() // End the last view if it exists.

            startSpanCapture(SchemaType.View(checkNotNull(name)), clock.now())?.apply {
                viewSpans[name] = this
            }
        }
    )

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
    fun endView(name: String?): Boolean = captureSpanData(
        countsTowardsLimits = false,
        inputValidation = { !name.isNullOrEmpty() },
        captureAction = {
            viewSpans.remove(name)?.stop()
        }
    )

    /**
     * Called when the activity is closed (and therefore all views are assumed to close).
     */
    fun onViewClose() {
        viewSpans.forEach { (_, span) ->
            captureSpanData(
                countsTowardsLimits = false,
                inputValidation = NoInputValidation,
                captureAction = {
                    span.stop()
                }
            )
        }
    }
}
