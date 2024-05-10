package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.logging.EmbLoggerImpl

internal class FakeEmbLogger : EmbLogger {

    override fun addLoggerAction(action: EmbLoggerImpl.LogAction) {
    }

    override fun logDebug(msg: String, throwable: Throwable?) {
    }

    override fun logInfo(msg: String) {
    }

    override fun logWarning(msg: String, throwable: Throwable?, logStacktrace: Boolean) {
    }

    override fun logError(msg: String, throwable: Throwable?, logStacktrace: Boolean) {
    }

    override fun logSdkNotInitialized(action: String) {
    }
}
