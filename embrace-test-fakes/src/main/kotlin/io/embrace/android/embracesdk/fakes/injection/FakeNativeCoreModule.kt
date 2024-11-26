package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeJniDelegate
import io.embrace.android.embracesdk.fakes.FakeNativeCrashProcessor
import io.embrace.android.embracesdk.fakes.FakeSharedObjectLoader
import io.embrace.android.embracesdk.fakes.FakeSymbolService
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.injection.NativeCoreModule
import io.embrace.android.embracesdk.internal.ndk.NativeCrashProcessor
import io.embrace.android.embracesdk.internal.ndk.jni.JniDelegate
import io.embrace.android.embracesdk.internal.ndk.symbols.SymbolService

class FakeNativeCoreModule(
    override val sharedObjectLoader: SharedObjectLoader = FakeSharedObjectLoader(),
    override val symbolService: SymbolService = FakeSymbolService(),
    override val processor: NativeCrashProcessor = FakeNativeCrashProcessor(),
    override val delegate: JniDelegate = FakeJniDelegate(),
) : NativeCoreModule
