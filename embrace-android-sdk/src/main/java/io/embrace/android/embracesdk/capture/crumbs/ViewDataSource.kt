package io.embrace.android.embracesdk.capture.crumbs

import io.embrace.android.embracesdk.arch.datasource.NoInputValidation
import io.embrace.android.embracesdk.arch.datasource.SpanDataSourceImpl
import io.embrace.android.embracesdk.arch.datasource.startSpanCapture
import io.embrace.android.embracesdk.arch.destination.StartSpanData
import io.embrace.android.embracesdk.arch.destination.StartSpanMapper
import io.embrace.android.embracesdk.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.config.behavior.BreadcrumbBehavior
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.FragmentBreadcrumb
import io.embrace.android.embracesdk.spans.EmbraceSpan

/**
 * Captures fragment views.
 */
internal class ViewDataSource(
    breadcrumbBehavior: BreadcrumbBehavior,
    private val clock: Clock,
    spanService: SpanService,
    logger: InternalEmbraceLogger
) : SpanDataSourceImpl(
    spanService,
    logger,
    UpToLimitStrategy(logger) { breadcrumbBehavior.getFragmentBreadcrumbLimit() }
),
    StartSpanMapper<FragmentBreadcrumb> {

    private val viewSpans: LinkedHashMap<String, EmbraceSpan> = LinkedHashMap()

    /**
     * Called when a view is started.
     */
    fun startView(name: String?): Boolean = captureSpanData(
        countsTowardsLimits = true,
        inputValidation = { !name.isNullOrEmpty() },
        captureAction = {
            val crumb = FragmentBreadcrumb(checkNotNull(name), clock.now())
            startSpanCapture(crumb, ::toStartSpanData)?.apply {
                viewSpans[name] = this
            }
        }
    )

    /**
     * Called when a view is started, ending the last view.
     */
    fun changeView(name: String?, force: Boolean) {
        val lastView = viewSpans.keys.lastOrNull()
        if (force || name.equals(lastView, ignoreCase = true)) {
            endView(lastView)
        }
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

    override fun toStartSpanData(obj: FragmentBreadcrumb): StartSpanData = with(obj) {
        StartSpanData(
            schemaType = SchemaType.View(name),
            spanStartTimeMs = start,
        )
    }
}
