package io.embrace.android.embracesdk.internal.config

/**
 * Whether state is recorded and sent as the app state data model. When it is not, state capture is as it was: a span for each
 * state in each session part. A session part gets one or the other, never both.
 *
 * The model goes out in two steps, each behind a switch of its own. This is the first, which is everything about the change log
 * and logs: one recorder that numbers every change, a journal of a session part's changes, the part's one change log, the stamp
 * on every log, and each change replayed for the app's own log exporters. [isStateAssociationEnabled] is the second.
 *
 * It needs its own switch and the switch for state capture as a whole. It also needs somewhere to keep a session part's changes
 * as they are made, which is the directory multi-file persistence writes, unless the app sends nothing to Embrace at all.
 */
fun ConfigService.isAppStateEnabled(): Boolean =
    config.autoDataCapture.stateCaptureEnabled &&
        config.appState.enabled &&
        (config.persistence.multiFileEnabled || isOnlyUsingOtelExporters())

/**
 * Whether spans are tied to state: the link on every span and, in the verbose rendering, the values on spans. It is the
 * model's second step, and does nothing unless [isAppStateEnabled]. An app whose only exporter takes spans gets state at it
 * only with this on.
 */
fun ConfigService.isStateAssociationEnabled(): Boolean = isAppStateEnabled() && config.appState.associationEnabled
