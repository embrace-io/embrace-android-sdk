package io.embrace.android.embracesdk.flow

public interface Workflow : Flow {
    public fun nextMilestone(name: String): Boolean
}
