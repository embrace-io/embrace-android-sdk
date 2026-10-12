package io.embrace.android.embracesdk.internal.instrumentation.bytecode

import androidx.annotation.Keep
import androidx.lifecycle.ViewModel
import io.embrace.android.embracesdk.instrumentation.leaks.viewModelLeakTracker

@Keep
object ViewModelBytecodeEntrypoint {

    @JvmStatic
    @Keep
    fun onViewModelCreated(viewModel: ViewModel) {
        viewModelLeakTracker?.onViewModelCreated(viewModel)
    }
}
