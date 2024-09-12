package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.buildinfo.BuildInfo
import io.embrace.android.embracesdk.internal.buildinfo.BuildInfoService

class FakeBuildInfoService : BuildInfoService {
    override fun getBuildInfo(): BuildInfo = BuildInfo(
        buildId = "fakeBuildId",
        buildType = "fakeBuildType",
        buildFlavor = "fakeBuildFlavor",
        rnBundleId = "fakeRnBundleId"
    )
}
