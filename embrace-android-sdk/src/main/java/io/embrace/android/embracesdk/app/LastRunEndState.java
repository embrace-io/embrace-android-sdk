package io.embrace.android.embracesdk.app;

/**
 * Enum representing the end state of the last run of the application.
 */
public enum LastRunEndState {
    /**
     * The SDK has not been started yet.
     */
    INVALID(0),

    /**
     * The last run resulted in a crash.
     */
    CRASH(1),

    /**
     * The last run did not result in a crash.
     */
    CLEAN_EXIT(2);

    private final int value;

    LastRunEndState(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
