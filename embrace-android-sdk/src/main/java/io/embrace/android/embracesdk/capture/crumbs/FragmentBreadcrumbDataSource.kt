package io.embrace.android.embracesdk.capture.crumbs

import io.embrace.android.embracesdk.arch.datasource.NoInputValidation
import io.embrace.android.embracesdk.arch.datasource.SpanDataSourceImpl
import io.embrace.android.embracesdk.arch.datasource.startSpanCapture
import io.embrace.android.embracesdk.arch.destination.StartSpanData
import io.embrace.android.embracesdk.arch.destination.StartSpanMapper
import io.embrace.android.embracesdk.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.payload.FragmentBreadcrumb
import io.embrace.android.embracesdk.spans.EmbraceSpan

/**
 * Captures fragment breadcrumbs.
 */
internal class FragmentBreadcrumbDataSource(
    configService: ConfigService,
    private val clock: Clock,
    spanService: SpanService
) : SpanDataSourceImpl(
    spanService,
    UpToLimitStrategy({ configService.breadcrumbBehavior.getFragmentBreadcrumbLimit() })
),
    StartSpanMapper<FragmentBreadcrumb> {

    companion object {
        internal const val SPAN_NAME = "screen-view"
    }

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
            val span = fragmentSpans.remove(name)
            span?.endFragmentSpan(clock.now())
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
                captureAction = { span.endFragmentSpan(clock.now()) }
            )
        }
    }

    override fun toStartSpanData(obj: FragmentBreadcrumb): StartSpanData = with(obj) {
        StartSpanData(
            spanName = SPAN_NAME,
            spanStartTimeMs = start,
            attributes = mapOf(
                "start_time" to start.toString(),
                "fragment_name" to name
            )
        )
    }

    private fun EmbraceSpan.endFragmentSpan(now: Long) {
        addAttribute("end_time", now.toString())
        stop()
    }
}
