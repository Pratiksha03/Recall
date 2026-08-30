package com.recall.app.data

/** One card recovered from an import file, before it is written to the database. */
data class ImportedCard(
    val question: String,
    val answer: String,
    val answerType: AnswerType,
    val tags: String = ""
)

data class ImportResult(
    val cards: List<ImportedCard> = emptyList(),
    val deckName: String? = null,      // from a #deck: header, if present
    val separatorName: String = "tab",
    val skipped: Int = 0,
    val warnings: List<String> = emptyList()
) {
    val isEmpty: Boolean get() = cards.isEmpty()
}

/**
 * Reads Anki's plain-text export format.
 *
 * Anki exports "Notes in Plain Text (.txt)" as one note per line with fields
 * separated by tabs, optionally preceded by header lines:
 *
 *     #separator:tab
 *     #html:true
 *     #deck:Spanish
 *     #tags:vocab
 *     What is "hello"?<tab>Hola
 *
 * The awkward parts this has to handle, all of which appear in real exports:
 *
 *  - Fields may be quoted, and a quoted field can contain the separator, newlines,
 *    and doubled quotes ("") meaning a literal quote. So this cannot be a line
 *    split; it has to be a character-level scan.
 *  - Field content is HTML, not plain text. <br> means a line break, and entities
 *    like &amp; must be decoded or they show up literally on the card.
 *  - Anything past the first two fields is extra (tags, note type, GUID).
 *
 * Anything it cannot make sense of is skipped and counted rather than throwing,
 * so one malformed line never costs you the rest of the file.
 */
object AnkiImport {

    private val SEPARATORS = mapOf(
        "tab" to '\t', "comma" to ',', "semicolon" to ';',
        "space" to ' ', "pipe" to '|', "colon" to ':'
    )

    fun parse(raw: String): ImportResult {
        val text = raw.replace("\r\n", "\n").replace('\r', '\n')
        if (text.isBlank()) return ImportResult()

        val warnings = mutableListOf<String>()
        var separator: Char? = null
        var separatorName = "tab"
        var deckName: String? = null
        var globalTags = ""
        var htmlDeclared: Boolean? = null

        // --- headers: '#' lines at the top, before any data ---
        var cursor = 0
        while (cursor < text.length) {
            if (text[cursor] != '#') break
            val lineEnd = text.indexOf('\n', cursor).let { if (it == -1) text.length else it }
            val line = text.substring(cursor + 1, lineEnd).trim()
            val key = line.substringBefore(':', "").trim().lowercase()
            val value = line.substringAfter(':', "").trim()
            when (key) {
                "separator" -> {
                    val named = SEPARATORS[value.lowercase()]
                    separator = named ?: value.firstOrNull()
                    separatorName = if (named != null) value.lowercase() else "'$value'"
                }
                "deck" -> deckName = value.takeIf { it.isNotBlank() }
                "tags" -> globalTags = value
                "html" -> htmlDeclared = value.equals("true", ignoreCase = true)
                "notetype", "columns", "guid column", "notetype column",
                "deck column", "tags column" -> Unit   // recognised, not needed here
                else -> if (line.isNotBlank()) warnings += "Ignored header: #$line"
            }
            cursor = if (lineEnd == text.length) text.length else lineEnd + 1
        }

        val body = text.substring(cursor)
        if (body.isBlank()) return ImportResult(warnings = warnings, deckName = deckName)

        // --- separator: use the header, else guess from the first data line ---
        if (separator == null) {
            val firstLine = body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()

            // A tab decides it outright: tabs essentially never occur inside field
            // text, so one tab is far stronger evidence than many commas.
            if (firstLine.contains('\t')) {
                separatorName = "tab"
                separator = '\t'
            } else {
                // Strip HTML entities before counting. &amp; &lt; &#39; all end in a
                // semicolon, so an entity-heavy tab file would otherwise look
                // semicolon-separated and every field would be cut at the entity.
                val stripped = Regex("&(#\\d+|[a-zA-Z]+);").replace(firstLine, "")
                val best = listOf(
                    "semicolon" to stripped.count { it == ';' },
                    "comma" to stripped.count { it == ',' },
                    "pipe" to stripped.count { it == '|' }
                ).maxByOrNull { it.second }

                if (best != null && best.second > 0) {
                    separatorName = best.first
                    separator = SEPARATORS[best.first]
                } else {
                    separatorName = "tab"
                    separator = '\t'
                    warnings += "No separator found — every line will become one field."
                }
            }
        }

        val rows = tokenize(body, separator!!)

        var skipped = 0
        val cards = mutableListOf<ImportedCard>()
        for (row in rows) {
            val fields = row.filterIndexed { i, f -> i < 2 || f.isNotBlank() }
            if (row.size < 2 || row[0].isBlank() || row[1].isBlank()) {
                if (row.any { it.isNotBlank() }) skipped++
                continue
            }
            val rawQuestion = row[0]
            val rawAnswer = row[1]
            val useHtml = htmlDeclared ?: looksLikeHtml(rawQuestion + rawAnswer)

            val question = if (useHtml) htmlToText(rawQuestion) else rawQuestion.trim()
            val answer = if (useHtml) htmlToText(rawAnswer) else rawAnswer.trim()
            if (question.isBlank() || answer.isBlank()) {
                skipped++
                continue
            }

            val rowTags = fields.getOrNull(2)?.takeIf { it.isNotBlank() && useHtml.not() }.orEmpty()
            cards += ImportedCard(
                question = question,
                answer = answer,
                answerType = detectType(rawAnswer, answer),
                tags = listOf(globalTags, rowTags).filter { it.isNotBlank() }.joinToString(" ")
            )
        }

        if (skipped > 0) {
            warnings += "$skipped line${if (skipped == 1) "" else "s"} skipped — " +
                "needs at least two non-empty fields."
        }

        return ImportResult(cards, deckName, separatorName, skipped, warnings)
    }

    /**
     * Character-level scan producing rows of fields, honouring quoted sections.
     * A '#' at the very start of a row (outside quotes) is a comment line.
     */
    private fun tokenize(text: String, separator: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var atFieldStart = true
        var atRowStart = true
        var i = 0

        fun endField() {
            row.add(field.toString())
            field.setLength(0)
            atFieldStart = true
        }
        fun endRow() {
            endField()
            if (row.any { it.isNotBlank() }) rows.add(row)
            row = mutableListOf()
            atRowStart = true
        }

        while (i < text.length) {
            val c = text[i]
            when {
                // comment line
                atRowStart && !inQuotes && c == '#' -> {
                    val end = text.indexOf('\n', i).let { if (it == -1) text.length else it }
                    i = if (end == text.length) text.length else end + 1
                    continue
                }
                inQuotes -> {
                    if (c == '"') {
                        if (i + 1 < text.length && text[i + 1] == '"') {
                            field.append('"'); i += 2; continue
                        }
                        inQuotes = false
                    } else field.append(c)
                }
                c == '"' && atFieldStart -> inQuotes = true
                c == separator -> { endField(); atRowStart = false }
                c == '\n' -> endRow()
                else -> { field.append(c); atFieldStart = false; atRowStart = false }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    private fun looksLikeHtml(s: String): Boolean =
        Regex("<(br|div|p|b|i|u|span|pre|code|img|ul|ol|li)\\b[^>]*>", RegexOption.IGNORE_CASE)
            .containsMatchIn(s) || s.contains("&nbsp;") || s.contains("&amp;")

    /** Anki fields are HTML. Turn them into the plain text this app stores. */
    fun htmlToText(html: String): String {
        var s = html
        s = s.replace(Regex("(?i)<br\\s*/?>"), "\n")
        s = s.replace(Regex("(?i)</(div|p|li|tr)>"), "\n")
        s = s.replace(Regex("(?i)<li\\b[^>]*>"), "• ")
        s = s.replace(Regex("<[^>]+>"), "")
        s = s.replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
        s = Regex("&#(\\d+);").replace(s) { m ->
            m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: m.value
        }
        // &amp; last, so "&amp;lt;" does not become "<"
        s = s.replace("&amp;", "&")
        return s.trim().replace(Regex("\n{3,}"), "\n\n")
    }

    private val URL = Regex("^(https?://|www\\.)\\S+$", RegexOption.IGNORE_CASE)

    /**
     * Map an imported answer onto one of this app's answer types.
     * Anki wraps code in <pre>/<code>, which is exactly our CODE type.
     */
    private fun detectType(rawAnswer: String, plainAnswer: String): AnswerType = when {
        Regex("(?i)<(pre|code)\\b").containsMatchIn(rawAnswer) -> AnswerType.CODE
        URL.matches(plainAnswer.trim()) -> AnswerType.LINK
        else -> AnswerType.TEXT
    }
}
