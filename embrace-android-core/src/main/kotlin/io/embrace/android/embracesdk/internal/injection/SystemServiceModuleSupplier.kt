package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.utils.VersionChecker

/**
 * Function that returns an instance of [SystemServiceModule]. Matches the signature of the constructor for [SystemServiceModuleImpl]
 */
typealias SystemServiceModuleSupplier = (
    coreModule: CoreModule,
    versionChecker: VersionChecker,
) -> SystemServiceModule

fun createSystemServiceModule(
    coreModule: CoreModule,
    versionChecker: VersionChecker,
): SystemServiceModule = SystemServiceModuleImpl(coreModule, versionChecker)
