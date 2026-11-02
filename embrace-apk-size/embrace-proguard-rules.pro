# Stands in for the Embrace gradle plugin, which this app doesn't apply. The plugin rewrites app and
# library bytecode to call these entrypoints; without those call sites R8 would remove them.
-keep class io.embrace.android.embracesdk.internal.instrumentation.bytecode.*BytecodeEntrypoint { public *; }
