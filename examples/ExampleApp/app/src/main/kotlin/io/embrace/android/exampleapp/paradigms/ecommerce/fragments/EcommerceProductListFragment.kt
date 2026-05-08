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
import io.embrace.android.exampleapp.paradigms.ecommerce.ui.EcommerceProductListUi
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme

class EcommerceProductListFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val categoryId = arguments?.getString(EcommerceFragmentsActivity.ARG_CATEGORY_ID)
        val category = categoryId?.let(SampleData::category)
        if (category == null) {
            findNavController().popBackStack()
            return ComposeView(requireContext())
        }
        val products = SampleData.productsIn(category.id)
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ExampleAppTheme {
                    EcommerceProductListUi(
                        categoryTitle = category.title,
                        products = products,
                        onProductClick = { id ->
                            findNavController().navigate("product/$id")
                        },
                        onBack = { findNavController().popBackStack() },
                    )
                }
            }
        }
    }
}
