package io.embrace.android.exampleapp.paradigms.ecommerce.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import io.embrace.android.exampleapp.di.appGraph
import io.embrace.android.exampleapp.paradigms.ecommerce.ui.EcommerceProductDetailUi
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme

class EcommerceProductDetailFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val graph = requireContext().appGraph()
        val sampleData = graph.sampleData
        val cartStore = graph.cartStore
        val productId = arguments?.getString(EcommerceFragmentsActivity.ARG_PRODUCT_ID)
        val product = productId?.let(sampleData::product)
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
                        onAddToCart = { cartStore.add(product) },
                    )
                }
            }
        }
    }
}
