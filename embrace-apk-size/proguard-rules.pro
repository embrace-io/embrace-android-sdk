# Stands in for app code using the libraries the SDK's instrumentation targets, so both flavors contain them.
-keep class okhttp3.** { *; }
-keep class com.google.firebase.messaging.** { *; }
