package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.utils.SystemTrace
import java.io.File
import java.io.IOException

/**
 * Appends records to the state journal a session part directory holds.
 *
 * An append costs the same however many records came before it: the file is kept open and only added to. Each
 * record is self contained, so [readStateJournal] recovers every record appended before a process died.
 */
class StateJournalWriter(
    private val target: SessionPartWriteTarget,
    private val errorHandler: ErrorHandler,
    private val maxBytes: Long = MAX_PART_FILE_BYTES,
) {

    private var file: SpanCollectionFile? = null
    private var reportedDrop = false

    /**
     * Whether a record failed to land. Nothing more is written once one has, so the journal never holds a complete log
     * with a hole in it: it reads back as a log whose end was not recorded.
     */
    private var incomplete = false

    fun markIncomplete() {
        incomplete = true
    }

    fun append(records: List<StateJournalRecord>): Boolean = SystemTrace.trace("mf-write-state-journal") {
        if (incomplete) {
            return@trace false
        }
        try {
            appendImpl(records).also { written -> incomplete = !written }
        } catch (exc: Throwable) {
            incomplete = true
            closeFile()
            target.reportWriteFailure(exc, ::trackFailure)
            false
        }
    }

    fun close() {
        closeFile()
    }

    private fun appendImpl(records: List<StateJournalRecord>): Boolean {
        if (records.isEmpty()) {
            return true
        }
        val file = openFile() ?: return false
        val bytes = StateJournal.ADAPTER.encode(
            StateJournal(
                format_version = if (file.size == 0L) FORMAT_VERSION else 0,
                records = records,
            ),
        )
        if (file.size + bytes.size > maxBytes) {
            if (!reportedDrop) {
                reportedDrop = true
                trackFailure(IOException("State journal is full"))
            }
            return false
        }
        file.append(bytes)
        return true
    }

    private fun openFile(): SpanCollectionFile? {
        file?.let { return it }
        val directory = target.directory ?: return null
        val partDir = target.partDir(directory, ::trackFailure) ?: return null
        return SpanCollectionFile(directory, File(partDir, STATE_JOURNAL_FILE_NAME), target.counters)
            .also { file = it }
    }

    private fun closeFile() {
        val open = file ?: return
        file = null
        errorHandler.guardSdkCode(SdkErrorType.CompletedSpansWriteFail, open::close)
    }

    private fun trackFailure(exc: Throwable) {
        errorHandler.trackSdkError(SdkErrorType.CompletedSpansWriteFail, exc)
    }
}
