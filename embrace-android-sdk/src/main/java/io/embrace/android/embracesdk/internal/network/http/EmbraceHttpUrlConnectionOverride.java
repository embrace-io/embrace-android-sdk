package io.embrace.android.embracesdk.internal.network.http;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

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
            return connection.getURL().toString();
        }
    }

    @NonNull
    @Override
    public String getURLString() {
        return connection.getURL().toString();
    }
}
