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
import io.embrace.android.exampleapp.paradigms.ecommerce.EcommerceCartStore
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
                EcommerceCartUi(
                    items = EcommerceCartStore.items,
                    totalCents = EcommerceCartStore.totalCents,
                    onRemove = { id -> EcommerceCartStore.remove(id) },
                    onPlaceOrder = {
                        val total = EcommerceCartStore.totalCents
                        EcommerceCartStore.clear()
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
