package io.embrace.android.embracesdk.macrobenchmark.app.fixture

import io.embrace.android.embracesdk.macrobenchmark.app.storage.FixtureStorage
import io.embrace.android.embracesdk.macrobenchmark.app.storage.SdkStorage

/**
 * Builds a benchmark's fixture one app instance at a time, and restores it before every iteration, between
 * the SDK's storage, [sdk], and the fixture's, [fixture]. It runs wherever it is called from: what launched
 * the app, and how the outcome is reported, are the caller's.
 *
 * Only what the SDK writes to [SdkStorage.sessionsDir] is supported: that is all a fixture records and restores,
 * and anything else an app instance writes is lost.
 *
 * [sdkStarted] says whether the SDK under test is running in this process.
 */
internal class FixtureRecorder(
    private val sdk: SdkStorage,
    private val fixture: FixtureStorage,
    private val sdkStarted: () -> Boolean,
    private val writeSettleMs: Long = WRITE_SETTLE_MS,
) {

    /**
     * Records an app instance into the fixture: runs [fixtureBuildingBlock], which acts through the SDK, waits past the
     * SDK's delayed writes, and adds the session parts it left to the fixture.
     */
    fun record(fixtureBuildingBlock: () -> Unit): FixtureOutcome {
        if (!sdkStarted()) {
            return FixtureOutcome.SdkNotStarted
        }
        fixtureBuildingBlock()

        // the SDK writes a part's changes a while after they happen, so an app instance killed sooner would
        // leave them out
        Thread.sleep(writeSettleMs)
        fixture.saveSessionParts(sdk.sessionsDir)
        return FixtureOutcome.Recorded
    }

    /**
     * Restores the fixture for an iteration: clears everything the SDK wrote, then copies the fixture's
     * session parts into the SDK's storage. It refuses while the SDK is running, since a running SDK would act
     * on the parts restored under it.
     */
    fun restore(): FixtureOutcome {
        if (sdkStarted()) {
            return FixtureOutcome.SdkRunning
        }
        sdk.clear()
        val restored = fixture.restoreSessionParts(sdk.sessionsDir)
        return if (restored > 0) {
            FixtureOutcome.Restored
        } else {
            FixtureOutcome.NothingToRestore
        }
    }

    private companion object {
        // SessionPartWriterImpl's write delays are 2s
        const val WRITE_SETTLE_MS = 3_000L
    }
}

/**
 * How recording or restoring a fixture went.
 */
internal sealed interface FixtureOutcome {

    /**
     * The app instance ran and its session parts are saved.
     */
    object Recorded : FixtureOutcome

    /**
     * The fixture's session parts are back where the SDK reads them.
     */
    object Restored : FixtureOutcome

    /**
     * An app instance can't be recorded, as the SDK under test never started.
     */
    object SdkNotStarted : FixtureOutcome

    /**
     * The fixture can't be restored, as the SDK is already running in this process.
     */
    object SdkRunning : FixtureOutcome

    /**
     * Nothing was recorded, so there was nothing to restore.
     */
    object NothingToRestore : FixtureOutcome
}
