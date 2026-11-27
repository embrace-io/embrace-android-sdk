package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.config.resolved.AppStateConfig
import io.embrace.android.embracesdk.internal.config.resolved.EmbraceConfig
import io.embrace.android.embracesdk.internal.config.resolved.PersistenceConfig

/**
 * A config with both steps of the app state data model on: state capture is on by default, so this switches on the model's two
 * switches and the multi-file persistence that keeps a session part's changes.
 */
fun appStateEnabledConfig(associationEnabled: Boolean = true): EmbraceConfig = EmbraceConfig(
    appState = { AppStateConfig(enabled = { true }, associationEnabled = { associationEnabled }) },
    persistence = { PersistenceConfig(multiFileEnabled = { true }) },
)

/**
 * A [FakeConfigService] with the app state data model on.
 */
fun appStateEnabledConfigService(): FakeConfigService = FakeConfigService(config = appStateEnabledConfig())
