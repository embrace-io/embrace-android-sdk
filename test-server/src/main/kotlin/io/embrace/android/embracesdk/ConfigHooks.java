package io.embrace.android.embracesdk;

import android.util.Base64;

import java.nio.charset.StandardCharsets;

import io.embrace.android.embracesdk.config.local.BaseUrlLocalConfig;
import io.embrace.android.embracesdk.config.local.NetworkLocalConfig;
import io.embrace.android.embracesdk.config.local.SdkLocalConfig;
import io.embrace.android.embracesdk.config.remote.RemoteConfig;
import io.embrace.android.embracesdk.config.remote.SessionRemoteConfig;
import io.embrace.android.embracesdk.config.remote.WebViewVitals;
import io.embrace.android.embracesdk.internal.EmbraceSerializer;

/**
 * Provides hooks into SessionConfig that aren't accessible via Kotlin.
 */
public class ConfigHooks {

    static RemoteConfig getConfig() {
        return new RemoteConfig(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            getSessionConfig(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            getWebViewVitals()
        );
    }

    static SessionRemoteConfig getSessionConfig() {
        return new SessionRemoteConfig(true,
            100f,
            false,
            null,
            null);
    }

    static WebViewVitals getWebViewVitals() {
        return new WebViewVitals(100f, 100);
    }

    static BaseUrlLocalConfig getBaseUrlConfig(String baseUrl) {
        return new BaseUrlLocalConfig(baseUrl,
            baseUrl,
            baseUrl,
            baseUrl);
    }

    static NetworkLocalConfig getNetworkConfig() {
        // With this config in true, an error related with openConnection reflection method not found
        // was being thrown on this test:
        // io.embrace.android.embracesdk.LogMessageTest.logHandledExceptionTest
        return new NetworkLocalConfig(null,
            null,
            null,
            null,
            null,
            false);
    }

    static String getSdkConfig(String baseUrl) {
        SdkLocalConfig sdkConfig = new SdkLocalConfig(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            getNetworkConfig(),
            null,
            null,
            null,
            null,
            getBaseUrlConfig(baseUrl),
            null,
            null,
            null,
            null
        );

        String json = new EmbraceSerializer().toJson(sdkConfig);
        return Base64.encodeToString(
            json.getBytes(StandardCharsets.UTF_8),
            Base64.DEFAULT
        );
    }
}
