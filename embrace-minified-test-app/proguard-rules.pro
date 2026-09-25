-keep class io.embrace.android.embracesdk.minified.** { *; }
-keep class androidx.tracing.** { *; }
-keep class kotlin.** { *; }

# JniRegistrationTest constructs this reflectively. The SDK doesn't need the constructor kept.
-keepclassmembers class io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.jni.JniDelegateImpl { <init>(); }
