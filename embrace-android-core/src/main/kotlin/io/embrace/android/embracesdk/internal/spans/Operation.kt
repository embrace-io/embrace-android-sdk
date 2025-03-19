package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.spans.Workflow

class Operation(
    private val name: String,
    private val spanService: SpanService,
) : Workflow {
    @Volatile
    private var root: EmbraceSpan? = null

    @Volatile
    private var currentSegment: EmbraceSpan? = null

    override fun start(segmentName: String?): Boolean {
        spanService.startSpan(name)?.apply {
            root = this
            segmentName?.let {
                startSegment(it)
            }
        }

        return root != null && (segmentName == null || currentSegment != null)
    }

    override fun startSegment(segmentName: String): Boolean {
        root?.run {
            currentSegment?.stop()
            currentSegment = spanService.startSpan(parent = root, name = "$name-$segmentName")
        }

        return currentSegment != null
    }

    override fun end(errorCode: ErrorCode?): Boolean {
        currentSegment?.stop(errorCode)
        return root?.stop(errorCode) != null
    }
}
