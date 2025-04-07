package io.embrace.android.embracesdk.flow

public interface Flow {
    public fun end(result: Result = Result.SUCCESS): Boolean
}
