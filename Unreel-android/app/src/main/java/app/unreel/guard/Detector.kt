package app.unreel.guard

import android.view.accessibility.AccessibilityNodeInfo
import app.unreel.data.SocialApp

object Detector {
    fun isShortForm(app: SocialApp, root: AccessibilityNodeInfo): Boolean {
        for (id in app.viewIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (nodes.any { it.isVisibleToUser }) return true
        }
        for (label in app.tabLabels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            if (nodes.any { isSelectedTab(it, label) }) return true
        }
        return false
    }

    private fun isSelectedTab(node: AccessibilityNodeInfo, label: String): Boolean {
        if (!node.isVisibleToUser) return false
        val selected = node.isSelected || node.parent?.isSelected == true
        if (!selected) return false
        // Only match labels that start with the tab name, not posts that mention it.
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        return text.startsWith(label, ignoreCase = true) || desc.startsWith(label, ignoreCase = true)
    }
}
