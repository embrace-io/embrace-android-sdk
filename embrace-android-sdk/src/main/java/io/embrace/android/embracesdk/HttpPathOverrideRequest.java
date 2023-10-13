package io.embrace.android.embracesdk;

@InternalApi
public interface HttpPathOverrideRequest<T> {
    String getHeaderByName(String name);

    @SuppressWarnings("AbbreviationAsWordInNameCheck")
    String getOverriddenURL(String pathOverride);

    @SuppressWarnings("AbbreviationAsWordInNameCheck")
    String getURLString();
}
