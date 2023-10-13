package io.embrace.android.embracesdk.network.http;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

import io.embrace.android.embracesdk.HttpPathOverrideRequest;
import io.embrace.android.embracesdk.InternalApi;
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger;

class EmbraceHttpUrlConnectionOverride implements HttpPathOverrideRequest {

    private final HttpURLConnection connection;

    EmbraceHttpUrlConnectionOverride(HttpURLConnection connection) {
        this.connection = connection;
    }

    @Override
    public String getHeaderByName(String name) {
        return connection.getRequestProperty(name);
    }

    @Override
    public String getOverriddenURL(String pathOverride) {
        try {
            return new URL(connection.getURL().getProtocol(), connection.getURL().getHost(),
                connection.getURL().getPort(), pathOverride).toString();
        } catch (MalformedURLException e) {
            InternalStaticEmbraceLogger.logError("Failed to override path of " +
                    connection.getURL() + " with " + pathOverride);
            return connection.getURL().toString();
        }
    }

    @Override
    public String getURLString() {
        return connection.getURL().toString();
    }
}
