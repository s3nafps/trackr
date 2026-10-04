package com.trackr.app.data.importer

/** Minimal RFC 4180 CSV: quoted fields, doubled quotes, commas and newlines inside quotes, CRLF or LF. */
object Csv {
    fun parse(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        val s = text.removePrefix("﻿")
        while (i < s.length) {
            val c = s[i]
            when {
                quoted && c == '"' && s.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> { row.add(field.toString()); field.clear() }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && s.getOrNull(i + 1) == '\n') i++
                    row.add(field.toString()); field.clear()
                    if (row.any { it.isNotEmpty() }) rows.add(row)
                    row = ArrayList()
                }
                else -> field.append(c)
            }
            i++
        }
        row.add(field.toString())
        if (row.any { it.isNotEmpty() }) rows.add(row)
        return rows
    }

    /** Rows as header-name → value maps; missing trailing cells read as "". */
    fun parseWithHeader(text: String): List<Map<String, String>> {
        val rows = parse(text)
        val header = rows.firstOrNull()?.map { it.trim() } ?: return emptyList()
        return rows.drop(1).map { r -> header.indices.associate { header[it] to r.getOrElse(it) { "" } } }
    }

    fun write(header: List<String>, rows: List<List<Any?>>): String = buildString {
        (listOf(header) + rows).forEach { r -> append(r.joinToString(",") { escape(it?.toString().orEmpty()) }).append("\r\n") }
    }

    private fun escape(v: String) =
        if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
}
