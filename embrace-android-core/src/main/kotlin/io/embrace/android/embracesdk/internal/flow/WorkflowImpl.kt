package io.embrace.android.embracesdk.internal.flow

import io.embrace.android.embracesdk.flow.Result
import io.embrace.android.embracesdk.flow.Workflow
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.spans.EmbraceSpan

class WorkflowImpl(
    firstMilestoneName: String,
    private val spanService: SpanService,
) : Workflow {

    @Volatile
    private var currentFlow: EmbraceSpan? = spanService.startSpan(name = firstMilestoneName)

    override fun end(result: Result): Boolean {
        return currentFlow?.stop(result.getSpanErrorCode()) == true
    }

    override fun nextMilestone(name: String): Boolean {
        if (currentFlow?.stop() == true) {
            currentFlow = spanService.startSpan(name = name)
        }

        return currentFlow?.isRecording == true
    }
}
