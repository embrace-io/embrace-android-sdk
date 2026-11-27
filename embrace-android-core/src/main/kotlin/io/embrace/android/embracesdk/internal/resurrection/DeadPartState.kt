package io.embrace.android.embracesdk.internal.resurrection

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.otel.spans.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.persistence.lastListedStates
import io.embrace.android.embracesdk.internal.state.AppStateKeys

/**
 * What a dead session part's change log says of the process that left it. The change log is the span rendered from the journal the
 * process appended each state change to, so it holds what that process recorded up to the moment it died.
 */
internal class DeadPartState(changeLog: Span) {

    private val listed = lastListedStates(changeLog)

    /**
     * The number of the last change the dead process recorded in the part, or null if its change log does not say.
     */
    val lastSeq: Long? = listed?.seq ?: changeLog.lastEventSeq()

    /**
     * The state stamp for a log about the dead process: the last value its change log leads to for each state, and their number,
     * in the plain form the app's own exporters take. It is that process's state, never that of the process writing the log, and
     * leaves out a state whose changes the log stopped listing, since its last value is then not known.
     */
    fun stamp(): Map<String, String> {
        val states = listed ?: return emptyMap()
        if (states.states.isEmpty()) {
            return emptyMap()
        }
        return buildMap {
            states.states.forEach { state ->
                put(AppStateKeys.VALUE_PREFIX + state.name, state.value)
                if (state.system) {
                    put(AppStateKeys.VALUE_TYPE_PREFIX + state.name, AppStateKeys.VALUE_TYPE_SYSTEM)
                }
            }
            put(AppStateKeys.SEQ, states.seq.toString())
        }
    }

    /**
     * A span the dead process left running, completed with the last number in its change log as the end of its link, when that is
     * later than the number it started at. Without it, a span with no end number would read as one that nothing changed under.
     */
    fun completeLink(span: Span): Span {
        val last = lastSeq ?: return span
        if (span.isStateChangeLog()) {
            return span
        }
        val attributes = span.attributes.orEmpty()
        val startSeq = attributes.findAttributeValue(AppStateKeys.SEQ)?.toLongOrNull() ?: 0L
        if (last <= startSeq || attributes.findAttributeValue(AppStateKeys.END_SEQ) != null) {
            return span
        }
        return span.copy(attributes = attributes + Attribute(AppStateKeys.END_SEQ, last.toString()))
    }

    private fun Span.lastEventSeq(): Long? =
        events?.lastOrNull()?.attributes?.findAttributeValue(AppStateKeys.SEQ)?.toLongOrNull()
            ?: attributes?.findAttributeValue(AppStateKeys.INITIAL_SEQ)?.toLongOrNull()

    companion object {

        /**
         * What the change log in [deadPart] says, or null if the part has none, as when state was not being recorded.
         */
        fun of(deadPart: Envelope<SessionPartPayload>): DeadPartState? {
            val changeLog = deadPart.data.spanSnapshots?.firstOrNull { it.isStateChangeLog() }
                ?: deadPart.data.spans?.firstOrNull { it.isStateChangeLog() }
            return changeLog?.let(::DeadPartState)
        }
    }
}

internal fun Span.isStateChangeLog(): Boolean =
    name == AppStateKeys.CHANGE_LOG_SPAN_NAME && hasEmbraceAttribute(EmbType.State)
