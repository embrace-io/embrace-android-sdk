package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.capture.crumbs.BreadcrumbDataSource
import io.embrace.android.embracesdk.capture.crumbs.FragmentViewDataSource
import io.embrace.android.embracesdk.capture.crumbs.TapDataSource
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger

internal class FakeBreadcrumbDataSource : BreadcrumbDataSource(
    FakeConfigService().breadcrumbBehavior,
    FakeCurrentSessionSpan(),
    InternalEmbraceLogger()
)

internal class FakeTapDataSource : TapDataSource(
    FakeConfigService().breadcrumbBehavior,
    FakeCurrentSessionSpan(),
    InternalEmbraceLogger()
)

internal class FakeFragmentDataSource : FragmentViewDataSource(
    FakeConfigService().breadcrumbBehavior,
    FakeClock(),
    FakeSpanService(),
    InternalEmbraceLogger()
)