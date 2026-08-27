package io.embrace.android.embracesdk.internal.resurrection

import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectoryStore
import io.embrace.android.embracesdk.internal.session.persistence.SessionReconstructionService

/**
 * Reads any session parts that were persisted on disk by the multi-file persistence layer,
 * reconstructs each one into an envelope, and hands it to the [IntakeService] for delivery. A
 * session part is deleted once intake has accepted it.
 *
 * This implementation currently does not perform any action - future changesets will alter that.
 */
@Suppress("UnusedPrivateProperty") // dependencies are wired ahead of the implementation
class SessionPartReader(
    private val directoryStore: SessionPartDirectoryStore,
    private val reconstructionService: SessionReconstructionService,
    private val intakeService: IntakeService,
    private val processIdProvider: () -> String,
    private val configService: ConfigService,
    private val logger: InternalLogger,
) {

    /**
     * Reads every session part on disk and hands it to the intake service.
     */
    fun readPersistedSessionParts() {
        if (!configService.persistenceBehavior.isMultiFilePersistenceEnabled()) {
            return
        }

        // TODO: future: reconstruct payloads and hand over to IntakeService.
    }
}
