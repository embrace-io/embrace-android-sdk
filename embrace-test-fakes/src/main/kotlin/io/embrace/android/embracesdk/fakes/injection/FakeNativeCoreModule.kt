package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeSharedObjectLoader
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.injection.NativeCoreModule

class FakeNativeCoreModule(
    override val sharedObjectLoader: SharedObjectLoader = FakeSharedObjectLoader(),
) : NativeCoreModule
