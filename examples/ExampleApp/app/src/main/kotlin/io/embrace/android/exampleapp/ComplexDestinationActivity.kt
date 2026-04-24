package io.embrace.android.exampleapp

import androidx.fragment.app.DialogFragment
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.createGraph
import androidx.navigation.fragment.dialog
import androidx.navigation.fragment.fragment
import androidx.navigation.navigation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class ComplexDestinationActivity : NavFragmentActivity() {

    override fun createNavGraph(navController: NavController): NavGraph =
        navController.createGraph(startDestination = "home") {
            fragment<PlaceholderFragment>("home") { label = "home" }
            fragment<PlaceholderFragment>("profile/{userId}") { label = "profile/{userId}" }
            fragment<PlaceholderFragment>("order/{orderId}/details") { label = "order/{orderId}/details" }
            dialog<PlaceholderDialogFragment>("confirm_dialog") { label = "confirm_dialog" }
            navigation(startDestination = "general", route = "settings_graph") {
                fragment<PlaceholderFragment>("general") { label = "general" }
                fragment<PlaceholderFragment>("privacy") { label = "privacy" }
            }
            fragment<PlaceholderFragment, HomeType>()
            fragment<PlaceholderFragment, ProfileType>()
            fragment<PlaceholderFragment, NoName>()
            fragment<PlaceholderFragment, NoNameOrder>()
        }

    override fun createActions(navController: NavController): List<Pair<String, () -> Unit>> = listOf(
        "Profile jon-joe" to { navController.navigate("profile/jon-joe") },
        "Profile 屈福特" to { navController.navigate("profile/屈福特") },
        "Order 123" to { navController.navigate("order/123/details") },
        "Order 456" to { navController.navigate("order/456/details") },
        "Dialog Confirm" to { navController.navigate("confirm_dialog") },
        "Nested->General" to { navController.navigate("general") },
        "Nested->Privacy" to { navController.navigate("privacy") },
        "HomeType" to { navController.navigate(HomeType) },
        "ProfileType jon-joe" to { navController.navigate(ProfileType(userName = "jon-joe")) },
        "ProfileType 屈福特" to { navController.navigate(ProfileType(userName = "屈福特")) },
        "NoNameType Home" to { navController.navigate(NoName) },
        "NoNameType Order 123" to { navController.navigate(NoNameOrder(itemId = 123)) },
        "NoNameType Order 456" to { navController.navigate(NoNameOrder(itemId = 456)) },
        "Back" to { navController.popBackStack() },
    )

    class PlaceholderDialogFragment : DialogFragment()
}

@Serializable
@SerialName("home_type")
object HomeType

@Serializable
@SerialName("profile_type")
data class ProfileType(val userName: String)

@Serializable
object NoName

@Serializable
data class NoNameOrder(val itemId: Int)
