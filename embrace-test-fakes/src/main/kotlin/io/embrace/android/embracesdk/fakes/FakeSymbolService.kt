package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.ndk.symbols.SymbolService

class FakeSymbolService(
    override val symbolsForCurrentArch: Map<String, String> = emptyMap(),
) : SymbolService
