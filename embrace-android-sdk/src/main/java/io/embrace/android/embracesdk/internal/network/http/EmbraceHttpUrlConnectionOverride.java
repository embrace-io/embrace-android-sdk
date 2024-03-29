package io.embrace.android.embracesdk.internal.network.http;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import io.embrace.android.embracesdk.Embrace;

class EmbraceHttpUrlConnectionOverride implements HttpPathOverrideRequest {

    private final HttpURLConnection connection;

    EmbraceHttpUrlConnectionOverride(HttpURLConnection connection) {
        this.connection = connection;
    }

    @Nullable
    @Override
    public String getHeaderByName(@NonNull String name) {
        return connection.getRequestProperty(name);
    }

    @NonNull
    @Override
    public String getOverriddenURL(@NonNull String pathOverride) {
        try {
            return new URL(connection.getURL().getProtocol(), connection.getURL().getHost(),
                connection.getURL().getPort(), pathOverride + "?" + connection.getURL().getQuery()).toString();
        } catch (MalformedURLException e) {
            Embrace.getInstance().getInternalInterface().logError(
                    "Failed to override path of " + connection.getURL() + " with " + pathOverride, null, null, false);
            return connection.getURL().toString();
        }
    }

    @NonNull
    @Override
    public String getURLString() {
        return connection.getURL().toString();
    }
}
