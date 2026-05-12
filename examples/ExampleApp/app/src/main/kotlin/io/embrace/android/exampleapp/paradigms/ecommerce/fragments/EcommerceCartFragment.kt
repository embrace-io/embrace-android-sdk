package io.embrace.android.exampleapp.paradigms.ecommerce.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import io.embrace.android.exampleapp.di.appGraph
import io.embrace.android.exampleapp.paradigms.ecommerce.ui.EcommerceCartUi
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme

class EcommerceCartFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            ExampleAppTheme {
                val cartStore = appGraph().cartStore
                EcommerceCartUi(
                    items = cartStore.items,
                    totalCents = cartStore.totalCents,
                    onRemove = { id -> cartStore.remove(id) },
                    onPlaceOrder = {
                        val total = cartStore.totalCents
                        cartStore.clear()
                        val result = Bundle().apply {
                            putLong(EcommerceFragmentsActivity.FRAGMENT_RESULT_KEY_TOTAL_CENTS, total)
                        }
                        setFragmentResult(
                            EcommerceFragmentsActivity.FRAGMENT_RESULT_ORDER_PLACED,
                            result,
                        )
                        findNavController().popBackStack()
                    },
                    onBack = { findNavController().popBackStack() },
                )
            }
        }
    }
}
