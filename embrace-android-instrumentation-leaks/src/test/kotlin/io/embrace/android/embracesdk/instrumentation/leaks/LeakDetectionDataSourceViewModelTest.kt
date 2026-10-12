package io.embrace.android.embracesdk.instrumentation.leaks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.internal.config.resolved.AutoDataCaptureConfig
import io.embrace.android.embracesdk.internal.config.resolved.EmbraceConfig
import io.embrace.android.embracesdk.internal.instrumentation.bytecode.ViewModelBytecodeEntrypoint
import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Covers ViewModel leak detection, calling [ViewModelBytecodeEntrypoint] as an instrumented ViewModel constructor would.
 */
@RunWith(RobolectricTestRunner::class)
internal class LeakDetectionDataSourceViewModelTest {

    private lateinit var dataSource: LeakDetectionDataSource
    private val store = ViewModelStore()

    @After
    fun tearDown() {
        dataSource.leakDetector.stop()
        viewModelLeakTracker = null
    }

    @Test
    fun `viewmodel leak detection is not wired when disabled (the default)`() {
        dataSource = LeakDetectionDataSource(FakeInstrumentationArgs(RuntimeEnvironment.getApplication()))
        dataSource.onDataCaptureEnabled()

        val viewModel = createViewModel()

        assertNull(
            "viewmodel tracking should be a no-op when the RemoteConfig flag is off",
            dataSource.leakDetector.trackClosed(viewModel, LeakContext("view_model", SESSION_IDS)),
        )
    }

    @Test
    fun `constructing a viewmodel tracks it as opened when enabled`() {
        enableViewModelLeakDetection()

        val viewModel = createViewModel()

        assertNotNull(
            "constructing a viewmodel should have opened a sentinel",
            dataSource.leakDetector.trackClosed(viewModel, LeakContext("view_model", SESSION_IDS)),
        )
    }

    @Test
    fun `clearing a viewmodel tracks it as closed when enabled`() {
        enableViewModelLeakDetection()

        val viewModel = createViewModel()
        store.clear()

        assertNull(
            "clearing the viewmodel should already have released its sentinel",
            dataSource.leakDetector.trackClosed(viewModel, LeakContext("view_model", SESSION_IDS)),
        )
    }

    @Test
    fun `only the cleared viewmodel is tracked as closed`() {
        enableViewModelLeakDetection()

        val cleared = createViewModel()
        val otherStore = ViewModelStore()
        val retained = createViewModel(otherStore)
        store.clear()

        assertNull(dataSource.leakDetector.trackClosed(cleared, LeakContext("view_model", SESSION_IDS)))
        assertNotNull(dataSource.leakDetector.trackClosed(retained, LeakContext("view_model", SESSION_IDS)))
    }

    @Test
    fun `a viewmodel without the AutoCloseable overload falls back to addCloseable(Closeable)`() {
        enableViewModelLeakDetection()

        val viewModel = createViewModel(viewModelClass = LegacyViewModel::class.java)
        store.clear()

        assertNull(
            "clearing a lifecycle 2.5 to 2.7 viewmodel should also have released its sentinel",
            dataSource.leakDetector.trackClosed(viewModel, LeakContext("view_model", SESSION_IDS)),
        )
    }

    private fun enableViewModelLeakDetection() {
        val args = FakeInstrumentationArgs(RuntimeEnvironment.getApplication())
        args.configService.config = EmbraceConfig(autoDataCapture = {
            AutoDataCaptureConfig(viewModelLeakDetectionEnabled = { true })
        })
        dataSource = LeakDetectionDataSource(args)
        dataSource.onDataCaptureEnabled()
    }

    private fun createViewModel(
        viewModelStore: ViewModelStore = store,
        viewModelClass: Class<out ViewModel> = TestViewModel::class.java,
    ): ViewModel {
        val viewModel = ViewModelProvider(viewModelStore, ViewModelProvider.NewInstanceFactory())[viewModelClass]
        // the call bytecode instrumentation inserts before ViewModel's constructor returns
        ViewModelBytecodeEntrypoint.onViewModelCreated(viewModel)
        return viewModel
    }

    private companion object {
        val SESSION_IDS = SessionIdsSnapshot(userSessionId = "session-1", sessionPartId = "part-1")
    }
}

class TestViewModel : ViewModel()

/**
 * Behaves like a lifecycle 2.5 to 2.7 ViewModel, which has no addCloseable(AutoCloseable).
 */
class LegacyViewModel : ViewModel() {
    override fun addCloseable(closeable: AutoCloseable): Unit = throw NoSuchMethodError()
}
