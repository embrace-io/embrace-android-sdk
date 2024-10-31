package io.embrace.android.embracesdk.internal.network.http;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.embrace.android.embracesdk.annotation.InternalApi;

@InternalApi
public interface HttpPathOverrideRequest {

    @Nullable
    String getHeaderByName(@NonNull String name);

    @NonNull
    @SuppressWarnings("AbbreviationAsWordInNameCheck")
    String getOverriddenURL(@NonNull String pathOverride);

    @NonNull
    @SuppressWarnings("AbbreviationAsWordInNameCheck")
    String getURLString();
}
