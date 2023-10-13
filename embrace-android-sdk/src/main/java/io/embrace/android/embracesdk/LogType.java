package io.embrace.android.embracesdk;

/**
 * Deprecated: use Severity instead. This enum is deprecated and will be removed in
 * a future release.
 *
 * Will flag the message as one of info, warning, or error for filtering on the dashboard
 */
@Deprecated
public enum LogType {
    INFO,
    WARNING,
    ERROR;

    final EmbraceEvent.Type toEventType() {
        switch (this) {
            case WARNING:
                return EmbraceEvent.Type.WARNING_LOG;
            case ERROR:
                return EmbraceEvent.Type.ERROR_LOG;
            default:
                return EmbraceEvent.Type.INFO_LOG;
        }
    }
}
