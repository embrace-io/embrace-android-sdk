package io.embrace.android.embracesdk.internal.session.persistence

/** The size of the version record a journal opens with. */
internal val JOURNAL_HEADER_SIZE: Int =
    StateJournal.ADAPTER.encode(StateJournal(format_version = FORMAT_VERSION)).size

internal fun stateRecord(
    sequence: Long,
    padding: Int = 0,
    attributes: Map<String, String> = emptyMap(),
): StateRecord = StateRecord(
    sequence = sequence,
    time_ms = 1_000L + sequence,
    change = StateChange(
        value_ = StateValue(state = "network", value_ = "wifi".padEnd(4 + padding, 'x')),
        attributes = attributes,
    ),
)

internal fun stateJournalHeader(version: Int = FORMAT_VERSION): ByteArray =
    StateJournal.ADAPTER.encode(StateJournal(format_version = version))

internal fun stateJournalAppend(records: List<StateRecord>, writtenAtMs: Long = 0L): ByteArray =
    StateJournal.ADAPTER.encode(StateJournal(records = records, written_at_ms = writtenAtMs))

internal fun stateJournalLog(records: List<StateRecord>, writtenAtMs: Long = 0L): ByteArray =
    stateJournalHeader() + stateJournalAppend(records, writtenAtMs)
