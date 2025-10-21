package io.embrace.android.embracesdk.internal.instrumentation

import android.view.View
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.NoInputValidation
import io.embrace.android.embracesdk.internal.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.behavior.BreadcrumbBehavior
import io.embrace.android.embracesdk.internal.logging.EmbLogger

/**
 * Captures custom breadcrumbs.
 */
class TapDataSource(
    private val breadcrumbBehavior: BreadcrumbBehavior,
    writer: SessionSpanWriter,
    logger: EmbLogger,
    private val clock: Clock,
) : DataSourceImpl<SessionSpanWriter>(
    destination = writer,
    logger = logger,
    limitStrategy = UpToLimitStrategy(breadcrumbBehavior::getTapBreadcrumbLimit)
) {

    companion object {
        const val KEY = "taps"
        private const val UNKNOWN_ELEMENT_NAME = "Unknown element"
    }

    fun logComposeTap(coords: Pair<Float, Float>, tag: String) {
        captureData(
            inputValidation = NoInputValidation,
            captureAction = {
                captureUiEvent(coords, tag, TapBreadcrumbType.TAP)
            }
        )
    }

    fun logTouchEvent(view: View, breadcrumbType: TapBreadcrumbType) {
        captureData(
            inputValidation = NoInputValidation,
            captureAction = {
                val viewName = try {
                    view.resources.getResourceName(view.id)
                } catch (e: Exception) {
                    UNKNOWN_ELEMENT_NAME
                }
                val point: Pair<Float, Float> = try {
                    Pair(view.x, view.y)
                } catch (e: Exception) {
                    Pair(0.0f, 0.0f)
                }
                captureUiEvent(point, viewName, breadcrumbType)
            }
        )
    }

    private fun SessionSpanWriter.captureUiEvent(
        coords: Pair<Float, Float>,
        name: String,
        breadcrumbType: TapBreadcrumbType
    ) {
        val finalPoint = when {
            breadcrumbBehavior.isViewClickCoordinateCaptureEnabled() -> coords
            else -> Pair(0.0f, 0.0f)
        }
        val coords = this.run {
            val first = finalPoint.first.toInt().toFloat()
            val second = finalPoint.second.toInt().toFloat()
            first.toInt().toString() + "," + second.toInt()
        }
        addSessionEvent(SchemaType.Tap(name, breadcrumbType.value, coords), clock.now())
    }
}
