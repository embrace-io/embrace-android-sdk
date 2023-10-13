package io.embrace.android.embracesdk;

import java.util.HashMap;
import java.util.Map;

import io.embrace.android.embracesdk.internal.BuildInfo;

class BuildInfoHooks {

    private static final BuildInfo buildInfo = new BuildInfo("default test build id", "default test build type", "default test build flavor");

    static Map<Integer, String> getResourceValues(String appId, String sdkConfig) {
        Map<Integer, String> map = new HashMap<>();
        map.put(BuildInfo.BUILD_INFO_BUILD_ID.hashCode(), buildInfo.getBuildId());
        map.put(BuildInfo.BUILD_INFO_BUILD_FLAVOR.hashCode(), buildInfo.getBuildFlavor());
        map.put(BuildInfo.BUILD_INFO_BUILD_TYPE.hashCode(), buildInfo.getBuildType());
        map.put("emb_app_id".hashCode(), appId);
        map.put("emb_sdk_config".hashCode(), sdkConfig);
        return map;
    }
}
