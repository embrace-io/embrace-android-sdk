package io.embrace.android.embracesdk.internal.network.http;

import androidx.annotation.NonNull;

import io.embrace.android.embracesdk.InternalApi;

@InternalApi
public interface HttpPathOverrideRequest {

    @NonNull
    String getHeaderByName(@NonNull String name);

    @NonNull
    @SuppressWarnings("AbbreviationAsWordInNameCheck")
    String getOverriddenURL(@NonNull String pathOverride);

    @NonNull
    @SuppressWarnings("AbbreviationAsWordInNameCheck")
    String getURLString();
}
