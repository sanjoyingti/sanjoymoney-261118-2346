package com.jaiho.sss.`in`.bot

import android.view.accessibility.AccessibilityNodeInfo

object BalanceParser {

    private val LABELS = listOf(
        "balance", "credit", "wallet", "bank", "total", "cash", "coins", "money", "saldo"
    )
    private val CURRENCY = listOf("₹", "$", "€", "£", "₱", "rs", "inr", "usd")

    private val NUMBER =
        Regex("""\d{1,3}(?:[,\s]\d{2,3}){1,4}(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?""")

    fun read(root: AccessibilityNodeInfo?): Double? {
        if (root == null) return null
        val texts = ArrayList<String>(64)
        collect(root, texts)
        if (texts.isEmpty()) return null

        for (i in texts.indices) {
            val lower = texts[i].lowercase()
            if (LABELS.any { lower.contains(it) }) {
                parseAmount(texts[i])?.let { return it }
                val end = minOf(i + 4, texts.size)
                for (j in i + 1 until end) {
                    parseAmount(texts[j])?.let { return it }
                }
            }
        }

        for (text in texts) {
            val lower = text.lowercase()
            if (CURRENCY.any { lower.contains(it) }) {
                parseAmount(text)?.let { return it }
            }
        }
        return null
    }

    private fun parseAmount(raw: String): Double? {
        val match = NUMBER.find(raw) ?: return null
        val cleaned = match.value.replace(",", "").replace(" ", "")
        val value = cleaned.toDoubleOrNull() ?: return null
        if (value <= 0.0 || value >= 1_000_000_000_000.0) return null
        return value
    }

    private fun collect(root: AccessibilityNodeInfo, out: MutableList<String>) {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 700) {
            val node = queue.removeFirst()
            visited++
            if (node.isVisibleToUser) {
                val text = node.text?.toString()?.trim()
                val desc = node.contentDescription?.toString()?.trim()
                val label = when {
                    !text.isNullOrEmpty() -> text
                    !desc.isNullOrEmpty() -> desc
                    else -> null
                }
                if (label != null && label.length in 1..32) out.add(label)
            }
            var i = 0
            while (i < node.childCount) {
                val child = runCatching { node.getChild(i) }.getOrNull()
                if (child != null) queue.add(child)
                i++
            }
        }
    }
}
