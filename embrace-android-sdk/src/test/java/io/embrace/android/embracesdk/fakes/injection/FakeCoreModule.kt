package io.embrace.android.embracesdk.fakes.injection

import android.app.Application
import android.content.Context
import io.embrace.android.embracesdk.app.AppFramework
import io.embrace.android.embracesdk.fakes.FakeAndroidResourcesService
import io.embrace.android.embracesdk.injection.CoreModule
import io.embrace.android.embracesdk.injection.isDebug
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.registry.ServiceRegistry
import io.mockk.isMockKMock
import io.mockk.mockk
import org.robolectric.RuntimeEnvironment

/**
 * If used in a Robolectric test, [application] and [context] will be fakes supplied by the Robolectric framework
 */
internal class FakeCoreModule(
    override val application: Application =
        if (RuntimeEnvironment.getApplication() == null) mockk(relaxed = true) else RuntimeEnvironment.getApplication(),
    override val context: Context =
        if (isMockKMock(application)) mockk(relaxed = true) else application.applicationContext,
    override val appFramework: AppFramework = AppFramework.NATIVE,
    override val logger: InternalEmbraceLogger = InternalStaticEmbraceLogger.logger,
    override val serviceRegistry: ServiceRegistry = ServiceRegistry(),
    override val jsonSerializer: EmbraceSerializer = EmbraceSerializer(),
    override val resources: FakeAndroidResourcesService = FakeAndroidResourcesService(),
    override val isDebug: Boolean =
        if (isMockKMock(context)) false else context.applicationInfo.isDebug()
) : CoreModule
