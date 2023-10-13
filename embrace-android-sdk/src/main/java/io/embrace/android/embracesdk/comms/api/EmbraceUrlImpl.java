package io.embrace.android.embracesdk.comms.api;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

class EmbraceUrlImpl extends EmbraceUrl {
    private URL url;

    EmbraceUrlImpl(@NonNull String url) throws MalformedURLException {
        this.url = new URL(url);
    }

    EmbraceUrlImpl(@NonNull URL url) {
        this.url = url;
    }

    @NonNull
    @Override
    public EmbraceConnection openConnection() throws IOException {
        return new EmbraceConnectionImpl((HttpURLConnection) url.openConnection(), this);
    }

    @Override
    public String getFile() {
        return url.getFile();
    }

    @Override
    public String toString() {
        return url.toString();
    }

    @Override
    public int hashCode() {
        return url.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof EmbraceUrlImpl) {
            return url.equals(((EmbraceUrlImpl) obj).url);
        }
        return url.equals(obj);
    }

}
