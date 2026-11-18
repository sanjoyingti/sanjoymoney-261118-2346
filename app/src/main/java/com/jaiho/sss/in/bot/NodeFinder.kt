package com.jaiho.sss.`in`.bot

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

object NodeFinder {

    private val SPIN_WORDS = listOf("spin", "play", "start", "go", "turbo", "autoplay", "rotate")
    private val COLLECT_WORDS = listOf("collect", "claim", "ok", "continue", "yes", "confirm")
    private val JACKPOT_WORDS = listOf(
        "jackpot", "big win", "mega win", "bonus", "free spins", "congratulation"
    )

    fun findSpin(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? = find(root, SPIN_WORDS)

    fun findCollect(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? = find(root, COLLECT_WORDS)

    fun scanJackpot(root: AccessibilityNodeInfo?): String? {
        if (root == null) return null
        var hit: String? = null
        walk(root) { node ->
            if (hit != null) return@walk
            if (!node.isVisibleToUser) return@walk
            val label = labelOf(node) ?: return@walk
            val lower = label.lowercase()
            if (label.length <= 48 && JACKPOT_WORDS.any { lower.contains(it) }) hit = label
        }
        return hit
    }

    private fun find(root: AccessibilityNodeInfo?, words: List<String>): AccessibilityNodeInfo? {
        if (root == null) return null
        val hits = ArrayList<AccessibilityNodeInfo>(8)
        walk(root) { node ->
            if (!node.isVisibleToUser) return@walk
            val label = labelOf(node) ?: return@walk
            if (label.length > 40) return@walk
            val lower = label.lowercase()
            if (words.any { lower == it || lower.contains(it) }) hits.add(node)
        }
        if (hits.isEmpty()) return null
        val clickable = hits.firstOrNull { it.isClickable }
        if (clickable != null) return clickable
        return hits.minByOrNull { area(it) }
    }

    private fun labelOf(node: AccessibilityNodeInfo): String? {
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty()) return text
        val desc = node.contentDescription?.toString()?.trim()
        if (!desc.isNullOrEmpty()) return desc
        return null
    }

    private fun area(node: AccessibilityNodeInfo): Int {
        val r = Rect()
        runCatching { node.getBoundsInScreen(r) }
        val w = r.width()
        val h = r.height()
        if (w <= 0 || h <= 0) return Int.MAX_VALUE
        return w * h
    }

    private inline fun walk(root: AccessibilityNodeInfo, crossinline visit: (AccessibilityNodeInfo) -> Unit) {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 900) {
            val node = queue.removeFirst()
            visited++
            visit(node)
            var i = 0
            while (i < node.childCount) {
                val child = runCatching { node.getChild(i) }.getOrNull()
                if (child != null) queue.add(child)
                i++
            }
        }
    }
}
