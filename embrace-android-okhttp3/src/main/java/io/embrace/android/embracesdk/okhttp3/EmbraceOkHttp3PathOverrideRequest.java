package io.embrace.android.embracesdk.okhttp3;

import io.embrace.android.embracesdk.internal.network.http.HttpPathOverrideRequest;
import okhttp3.Request;

class EmbraceOkHttp3PathOverrideRequest implements HttpPathOverrideRequest {

    private final Request request;

    EmbraceOkHttp3PathOverrideRequest(Request request) {
        this.request = request;
    }

    @Override
    public String getHeaderByName(String name) {
        return request.header(name);
    }

    @Override
    public String getOverriddenURL( String pathOverride) {
        return request.url().newBuilder().encodedPath(pathOverride).build().toString();
    }

    @Override
    public String getURLString() {
        return request.url().toString();
    }
}
