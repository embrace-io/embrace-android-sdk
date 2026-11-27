package io.embrace.android.embracesdk.internal.otel.state

import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.context.ContextKey
import io.opentelemetry.kotlin.context.createContextKey

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
