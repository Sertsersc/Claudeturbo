package com.bariscraft.turbo

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class TurboAccessibilityService : AccessibilityService() {

    companion object {
        var instance: TurboAccessibilityService? = null

        fun performSearch(query: String, appPackage: String) {
            instance?.doSearch(query, appPackage)
        }

        fun performGlobalAction(action: Int) {
            instance?.performGlobalAction(action)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Gerektiğinde ekran içeriğini okuyabiliriz
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    // YouTube veya herhangi bir uygulamada arama yap
    fun doSearch(query: String, appPackage: String) {
        val rootNode = rootInActiveWindow ?: return
        findAndClickSearch(rootNode, query)
    }

    private fun findAndClickSearch(node: AccessibilityNodeInfo, query: String) {
        // Arama kutusunu bul
        val searchNodes = node.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/search_edit_text")
            ?: node.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/search_bar")

        if (searchNodes != null && searchNodes.isNotEmpty()) {
            val searchBox = searchNodes[0]
            searchBox.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            val args = android.os.Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query)
            searchBox.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            return
        }

        // Genel arama butonu
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { findAndClickSearch(it, query) }
        }
    }

    // Geri git
    fun goBack() = performGlobalAction(GLOBAL_ACTION_BACK)

    // Ana ekrana git
    fun goHome() = performGlobalAction(GLOBAL_ACTION_HOME)

    // Son uygulamalar
    fun showRecents() = performGlobalAction(GLOBAL_ACTION_RECENTS)

    // Bildirim paneli
    fun showNotifications() = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    // Hızlı ayarlar
    fun showQuickSettings() = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
}
