package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.config.BuildInfo

/**
 * Canonical fake [BuildInfo] shared across unit and integration tests.
 */
val fakeBuildInfo: BuildInfo = BuildInfo(
    buildId = "fakeBuildId",
    buildType = "fakeBuildType",
    buildFlavor = "fakeBuildFlavor",
    rnBundleId = "fakeRnBundleId",
    versionName = "2.5.1",
    versionCode = "99",
    packageName = "com.fake.package",
)
