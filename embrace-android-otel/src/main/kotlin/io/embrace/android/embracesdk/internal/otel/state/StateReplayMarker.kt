package io.embrace.android.embracesdk.internal.otel.state

import io.embrace.android.embracesdk.internal.state.AppStateKeys
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.context.ContextKey
import io.opentelemetry.kotlin.context.createContextKey
import io.opentelemetry.kotlin.logging.data.LogRecordData

private val stateReplayKey: ContextKey<Boolean> = createContextKey("embrace-state-replay")

/**
 * This context marked as the one a replayed state change or snapshot is emitted with. The mark travels beside the record and not
 * on it, so nothing an app writes on a log of its own can pass for it.
 */
internal fun Context.markedAsStateReplay(): Context = set(stateReplayKey, true)

/**
 * Whether a log record emitted with this context is a state change or a snapshot replayed for the app's own exporters. Such a
 * record describes the process and no session part, so it is not stamped and is not sent to Embrace.
 */
internal fun Context.isStateReplay(): Boolean = get(stateReplayKey) == true

/**
 * Whether this record has the event name of a replayed state change or snapshot. A record is taken for a replay only when it has
 * one of the two names and was emitted with the marked context: the name alone could be an app's own log, and the mark alone
 * could be a log an app's log record processor emitted with the context it was handed.
 */
internal fun LogRecordData.isStateReplayRecord(): Boolean {
    // read once: in compat mode a getter on the live record copies it
    val name = eventName
    return name == AppStateKeys.CHANGE_EVENT_NAME || name == AppStateKeys.SNAPSHOT_EVENT_NAME
}
