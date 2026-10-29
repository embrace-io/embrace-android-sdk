package io.embrace.bytecode

/**
 * Which instrumentation features are enabled via the DSL.
 */
data class InstrumentationFeatureFlags(
    val shouldInstrumentFirebaseMessaging: Boolean,
    val shouldInstrumentWebview: Boolean,
    val shouldInstrumentAutoSdkInitialization: Boolean,
    val shouldInstrumentOkHttp: Boolean,
    val shouldInstrumentOnLongClick: Boolean,
    val shouldInstrumentOnClick: Boolean,
    val applicationInitTimingEnabled: Boolean,
)
