package io.embrace.android.embracesdk.okhttp3;

import io.embrace.android.embracesdk.Embrace;
import io.embrace.android.embracesdk.utils.NetworkUtils;

/**
 * Facade to call internal SDK methods that can be mocked for tests
 */
class SdkFacade {
    boolean isNetworkSpanForwardingEnabled() {
        return NetworkUtils.isNetworkSpanForwardingEnabled(Embrace.getInstance().getConfigService());
    }
}
