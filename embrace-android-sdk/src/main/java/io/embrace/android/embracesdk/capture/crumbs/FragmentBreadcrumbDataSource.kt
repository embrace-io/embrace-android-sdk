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
import io.embrace.android.embracesdk.payload.FragmentBreadcrumb
import io.embrace.android.embracesdk.spans.EmbraceSpan

/**
 * Captures fragment breadcrumbs.
 */
internal class FragmentBreadcrumbDataSource(
    breadcrumbBehavior: BreadcrumbBehavior,
    private val clock: Clock,
    spanService: SpanService
) : SpanDataSourceImpl(
    spanService,
    UpToLimitStrategy({ breadcrumbBehavior.getFragmentBreadcrumbLimit() })
),
    StartSpanMapper<FragmentBreadcrumb> {

    private val fragmentSpans: MutableMap<String, EmbraceSpan> = mutableMapOf()

    /**
     * Called when a fragment is started.
     */
    fun startFragment(name: String?): Boolean = captureSpanData(
        countsTowardsLimits = true,
        inputValidation = { !name.isNullOrEmpty() },
        captureAction = {
            val crumb = FragmentBreadcrumb(checkNotNull(name), clock.now())
            startSpanCapture(crumb, ::toStartSpanData)?.apply {
                fragmentSpans[name] = this
            }
        }
    )

    /**
     * Called when a fragment is ended.
     */
    fun endFragment(name: String?): Boolean = captureSpanData(
        countsTowardsLimits = false,
        inputValidation = { !name.isNullOrEmpty() },
        captureAction = {
            fragmentSpans.remove(name)?.stop()
        }
    )

    /**
     * Called when the activity is closed (and therefore all fragments are assumed to close).
     */
    fun onViewClose() {
        fragmentSpans.forEach { (_, span) ->
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
            schemaType = SchemaType.ViewBreadcrumb(name),
            spanStartTimeMs = start,
        )
    }
}
