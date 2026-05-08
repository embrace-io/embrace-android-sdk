package io.embrace.android.exampleapp.paradigms.ecommerce.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import io.embrace.android.exampleapp.paradigms.data.SampleData
import io.embrace.android.exampleapp.paradigms.ecommerce.EcommerceCartStore
import io.embrace.android.exampleapp.paradigms.ecommerce.ui.EcommerceProductDetailUi
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme

class EcommerceProductDetailFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val productId = arguments?.getString(EcommerceFragmentsActivity.ARG_PRODUCT_ID)
        val product = productId?.let(SampleData::product)
        if (product == null) {
            findNavController().popBackStack()
            return ComposeView(requireContext())
        }
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ExampleAppTheme {
                    EcommerceProductDetailUi(
                        product = product,
                        onBack = { findNavController().popBackStack() },
                        onAddToCart = { EcommerceCartStore.add(product) },
                    )
                }
            }
        }
    }
}
