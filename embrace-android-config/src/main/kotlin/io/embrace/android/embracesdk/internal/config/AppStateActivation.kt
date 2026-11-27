package io.embrace.android.embracesdk.internal.config

/**
 * Whether state is recorded and sent as the app state data model: a stamp on every log, a link on every span and one change log
 * for each session part. When it is not, state capture is as it was: a span for each state in each session part. A session part
 * gets one or the other, never both.
 *
 * It needs its own switch and the switch for state capture as a whole. It also needs somewhere to keep a session part's changes
 * as they are made, which is the directory multi-file persistence writes, unless the app sends nothing to Embrace at all.
 */
fun ConfigService.isAppStateEnabled(): Boolean =
    config.autoDataCapture.stateCaptureEnabled &&
        config.appState.enabled &&
        (config.persistence.multiFileEnabled || isOnlyUsingOtelExporters())
