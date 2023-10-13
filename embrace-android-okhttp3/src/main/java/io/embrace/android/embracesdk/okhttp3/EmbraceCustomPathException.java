package io.embrace.android.embracesdk.okhttp3;

import java.io.IOException;

import io.embrace.android.embracesdk.InternalApi;

/**
 * We use the EmbraceCustomPathException to capture the custom path added in the
 * intercept chain process for client errors.
 */
@InternalApi
public class EmbraceCustomPathException extends IOException {

    private final String customPath;

    public EmbraceCustomPathException(String customPath, Throwable cause) {
        super(cause);
        this.customPath = customPath;
    }

    public String getCustomPath() {
        return customPath;
    }
}
