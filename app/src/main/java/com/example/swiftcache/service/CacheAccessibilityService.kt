package com.example.swiftcache.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class CacheAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_PROGRESS = "com.example.swiftcache.PROGRESS"
        const val ACTION_FINISHED = "com.example.swiftcache.FINISHED"
        const val EXTRA_PROGRESS_TEXT = "progress_text"
        
        var packagesToClean: MutableList<String> = mutableListOf()
        var isCleaning = false
        
        // State Machine States
        private const val STATE_IDLE = 0
        private const val STATE_WAITING_FOR_STORAGE = 1
        private const val STATE_WAITING_FOR_CACHE_BTN = 2
        private const val STATE_NEXT_APP = 3
    }

    private var currentState = STATE_IDLE
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        isCleaning = false
        currentState = STATE_IDLE
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isCleaning || event == null) return

        val rootNode = rootInActiveWindow ?: return

        when (currentState) {
            STATE_WAITING_FOR_STORAGE -> {
                // "Storage" və ya "Yaddaş" düyməsini axtarırıq
                val storageNode = findNodeByText(rootNode, arrayOf("Storage", "Yaddaş", "Память"))
                if (storageNode != null) {
                    clickNodeOrParent(storageNode)
                    currentState = STATE_WAITING_FOR_CACHE_BTN
                }
            }
            
            STATE_WAITING_FOR_CACHE_BTN -> {
                // "Clear cache" və ya "Keşi təmizlə" düyməsini axtarırıq
                val clearNode = findNodeByText(rootNode, arrayOf("Clear cache", "Keşi təmizlə", "Очистить кэш"))
                if (clearNode != null) {
                    clickNodeOrParent(clearNode)
                    currentState = STATE_NEXT_APP
                    
                    // Klikləndikdən sonra növbəti tətbiqə keçmək üçün 1 saniyə gözləyirik
                    handler.postDelayed({ processNextApp() }, 1000)
                }
            }
        }
    }

    private fun processNextApp() {
        if (packagesToClean.isEmpty()) {
            // Siyahı bitdi
            isCleaning = false
            currentState = STATE_IDLE
            sendBroadcast(ACTION_FINISHED, "Təmizləmə tamamlandı!")
            return
        }

        val nextPackage = packagesToClean.removeAt(0)
        val progressText = "Təmizlənir: $nextPackage (${packagesToClean.size} qalıb)"
        sendBroadcast(ACTION_PROGRESS, progressText)

        // Tətbiqin ayarlar səhifəsini açırıq
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$nextPackage")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)

        // Səhifə açıldıqdan sonra "Storage" axtarmağa başlayırıq
        currentState = STATE_WAITING_FOR_STORAGE
    }

    fun startCleaning(packageList: List<String>) {
        if (isCleaning) return
        packagesToClean.clear()
        packagesToClean.addAll(packageList)
        isCleaning = true
        processNextApp()
    }

    private fun findNodeByText(root: AccessibilityNodeInfo, texts: Array<String>): AccessibilityNodeInfo? {
        for (text in texts) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            if (nodes.isNotEmpty()) {
                return nodes.firstOrNull { it.isVisibleToUser }
            }
        }
        return null
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo) {
        if (node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable) {
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return
                }
                parent = parent.parent
            }
        }
    }

    private fun sendBroadcast(action: String, text: String) {
        val intent = Intent(action).apply {
            putExtra(EXTRA_PROGRESS_TEXT, text)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onInterrupt() {
        isCleaning = false
        currentState = STATE_IDLE
    }
}
