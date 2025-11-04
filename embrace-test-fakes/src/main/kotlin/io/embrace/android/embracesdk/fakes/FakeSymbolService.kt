package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.symbols.SymbolService

class FakeSymbolService(
    override val symbolsForCurrentArch: MutableMap<String, String> = mutableMapOf(),
) : SymbolService
