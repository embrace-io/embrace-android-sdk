package io.embrace.android.embracesdk.app;

/**
 * The AppFramework that is in use.
 */
public enum AppFramework {
    NATIVE(1),
    REACT_NATIVE(2),
    UNITY(3),
    FLUTTER(4);

    private final int value;

    AppFramework(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
