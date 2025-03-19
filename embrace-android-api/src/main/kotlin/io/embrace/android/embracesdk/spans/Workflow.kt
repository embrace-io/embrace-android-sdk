package io.embrace.android.embracesdk.spans

public interface Workflow {
    public fun start(segmentName: String? = null): Boolean
    public fun startSegment(segmentName: String): Boolean
    public fun end(errorCode: ErrorCode? = null): Boolean
}
