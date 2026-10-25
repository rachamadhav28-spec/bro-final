package com.jarvis.agent

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        var instance: JarvisAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Passive event listener
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun toggleQuickSettingTile(tileName: String): Boolean {
        performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        Thread.sleep(900)
        val root = rootInActiveWindow ?: return false
        val clicked = findAndClickNodeByText(root, tileName)
        Thread.sleep(500)
        performGlobalAction(GLOBAL_ACTION_BACK)
        return clicked
    }

    fun scrapeCurrentScreenText(): String {
        val root = rootInActiveWindow ?: return "Accessibility Service not active or window unavailable."
        val builder = StringBuilder()
        traverseAndCollectText(root, builder)
        return builder.toString().ifBlank { "No readable text found on current screen." }
    }

    private fun traverseAndCollectText(node: AccessibilityNodeInfo?, builder: StringBuilder) {
        if (node == null) return
        if (!node.text.isNullOrBlank()) {
            builder.append(node.text).append("\n")
        } else if (!node.contentDescription.isNullOrBlank()) {
            builder.append(node.contentDescription).append("\n")
        }
        for (i in 0 until node.childCount) {
            traverseAndCollectText(node.getChild(i), builder)
        }
    }

    fun findAndClickNodeByText(root: AccessibilityNodeInfo?, targetText: String): Boolean {
        if (root == null) return false
        val nodes = root.findAccessibilityNodeInfosByText(targetText)
        for (node in nodes) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable) {
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
                parent = parent.parent
            }
        }
        return false
    }

    fun typeAndSendMessage(message: String): String {
        val root = rootInActiveWindow ?: return "Error: Enable Jarvis Accessibility Service first."
        val editNode = findEditableNode(root) ?: return "Error: No text input box found on screen."

        val arguments = Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, message)
        editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

        Thread.sleep(500)
        val sent = findAndClickNodeByText(root, "Send")
        return if (sent) "Message typed and sent." else "Message typed into field (tap Send to confirm)."
    }

    private fun findEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val found = findEditableNode(node.getChild(i))
            if (found != null) return found
        }
        return null
    }
}
