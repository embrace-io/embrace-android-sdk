package io.embrace.android.embracesdk.internal.ndk.symbols

/**
 * Service that retrieves native symbols for the current architecture so they can be attached to payloads
 * that require desymbolication.
 */
interface SymbolService {
    val symbolsForCurrentArch: Map<String, String>?
}
