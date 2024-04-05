@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.embrace.android.embracesdk.compose.internal

import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.AndroidComposeView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import io.embrace.android.embracesdk.Embrace
import java.util.concurrent.ScheduledExecutorService

private const val UNKNOWN_ELEMENT_NAME = "Unlabeled Compose element"

internal class EmbraceNodeIterator {

    /**
     *  If the received view is AndroidComposeView,
     *  we collect the compose tree and iterate over it to find the clicked view,
     *  by comparing with the received position (x,y)
     *  */
    fun findClickedElement(root: View, x: Float, y: Float, backgroundWorker: ScheduledExecutorService) {
        val semanticsOwner = if (root is AndroidComposeView) root.semanticsOwner else return
        val semanticsNodes = semanticsOwner.getAllSemanticsNodes(true)

        backgroundWorker.submit {
            findClickedElement(semanticsNodes, x, y)?.let {
                val clickedView = ClickedView(it, x, y)
                Embrace.getInstance().internalInterface.logComposeTap(Pair(clickedView.x, clickedView.y), clickedView.tag)
            }
        }
    }

    /**
     * Iterates over the compose tree to find the clicked element and retrieve its tag
     * */
    private fun findClickedElement(semanticsNodes: List<SemanticsNode>, x: Float, y: Float): String? {
        for (node in semanticsNodes) {
            if (isNodeInPosition(node, x, y)) {
                val clickableElementName = getClickableElementName(node.config)
                if (clickableElementName != null) {
                    return clickableElementName
                }
            }
        }
        return null
    }

    private fun getClickableElementName(semanticsConfiguration: SemanticsConfiguration): String? {
        val onClickSemanticsConfiguration = semanticsConfiguration.getOrNull(SemanticsActions.OnClick)
        if (onClickSemanticsConfiguration != null) {
            // The node is clickable. Return accessibilityActionLabel if present.
            val accessibilityActionLabel = onClickSemanticsConfiguration.label
            if (accessibilityActionLabel != null) {
                return accessibilityActionLabel
            }

            // If the OnClick configuration doesn't have an accessibilityActionLabel, check for the content description instead.
            val contentDescriptionSemanticsConfiguration = semanticsConfiguration.getOrNull(SemanticsProperties.ContentDescription)
            if (contentDescriptionSemanticsConfiguration != null) {
                val contentDescription = contentDescriptionSemanticsConfiguration.getOrNull(0)
                if (contentDescription != null) {
                    return contentDescription
                }
            }

            // The view is clickable, so return it with a default name.
            return UNKNOWN_ELEMENT_NAME
        }
        return null
    }

    /**
     *  Validates if a node position is same as x, y
     */
    private fun isNodeInPosition(node: SemanticsNode, x: Float, y: Float) = node.boundsInWindow.contains(Offset(x, y))
}
