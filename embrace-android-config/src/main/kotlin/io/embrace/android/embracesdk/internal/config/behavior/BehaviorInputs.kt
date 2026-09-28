package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.resolved.ConfigInputs

/**
 * The inputs for the constructors behaviors keep while their call sites move to reading
 * [io.embrace.android.embracesdk.internal.config.resolved.EmbraceConfig] directly. A behavior without a rollout has no
 * [thresholdCheck], and reading its bucket fails.
 */
internal fun behaviorInputs(
    local: InstrumentedConfig,
    remote: RemoteConfig?,
    thresholdCheck: BehaviorThresholdCheck? = null,
): ConfigInputs = ConfigInputs(local, remote, thresholdCheck?.bucket ?: lazy { error("This behavior has no rollout") })
