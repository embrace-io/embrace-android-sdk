# Stands in for the Embrace gradle plugin setting sdk_config.otel.enable_otel_kotlin_sdk, which
# this app doesn't apply.
-assumevalues class io.embrace.android.embracesdk.internal.config.instrumented.EnabledFeatureConfigImpl {
    boolean isOtelKotlinSdkEnabled() return true;
}
