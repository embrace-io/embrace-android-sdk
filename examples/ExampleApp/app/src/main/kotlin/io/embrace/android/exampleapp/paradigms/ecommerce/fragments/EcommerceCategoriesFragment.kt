package io.embrace.android.exampleapp.paradigms.ecommerce.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.fragment.findNavController
import io.embrace.android.exampleapp.di.appGraph
import io.embrace.android.exampleapp.paradigms.ecommerce.ui.EcommerceCategoriesUi
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme

class EcommerceCategoriesFragment : Fragment() {

    private var orderPlacedTotalCents by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFragmentResultListener(
            EcommerceFragmentsActivity.FRAGMENT_RESULT_ORDER_PLACED,
        ) { _, bundle ->
            orderPlacedTotalCents = bundle.getLong(
                EcommerceFragmentsActivity.FRAGMENT_RESULT_KEY_TOTAL_CENTS,
                0L,
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            ExampleAppTheme {
                val graph = appGraph()
                val sampleData = graph.sampleData
                val cartStore = graph.cartStore
                EcommerceCategoriesUi(
                    title = "Categories (Fragments)",
                    categories = sampleData.productCategories,
                    onCategoryClick = { id ->
                        findNavController().navigate("category/$id")
                    },
                    cartItemCount = cartStore.itemCount,
                    onCartClick = {
                        findNavController().navigate(EcommerceFragmentsActivity.ROUTE_CART)
                    },
                    orderPlacedTotalCents = orderPlacedTotalCents,
                )
            }
        }
    }
}
