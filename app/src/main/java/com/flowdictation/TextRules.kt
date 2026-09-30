package com.flowdictation

/**
 * Simple offline cleanup. Used when AI cleanup is switched off or fails,
 * so you always get something sensible. No Android dependencies.
 */
object LocalRules {
    private val filler = Regex("\\b(?:um+|uh+|erm)\\b(?!-)[,.]?\\s*", RegexOption.IGNORE_CASE)
    private val newParagraph = Regex(",?\\s*\\bnew paragraph\\b[.,]?\\s*", RegexOption.IGNORE_CASE)
    private val newLine = Regex(",?\\s*\\bnew line\\b[.,]?\\s*", RegexOption.IGNORE_CASE)
    private val spacesAroundNewline = Regex("[ \\t]*\\n[ \\t]*")
    private val multiSpace = Regex("[ \\t]{2,}")

    fun apply(input: String): String {
        var t = input.trim()
        t = filler.replace(t, "")
        t = newParagraph.replace(t, "\n\n")
        t = newLine.replace(t, "\n")
        t = multiSpace.replace(t, " ")
        t = spacesAroundNewline.replace(t, "\n")
        return capitalizeLines(t.trim())
    }

    /** Capitalises the first word of each line, but only plain lowercase words (leaves iPhone, emails, etc. alone). */
    private fun capitalizeLines(text: String): String {
        val lines = text.split("\n")
        val out = ArrayList<String>(lines.size)
        for (line in lines) {
            val idx = line.indexOfFirst { !it.isWhitespace() }
            if (idx < 0) {
                out.add(line)
                continue
            }
            var end = idx
            while (end < line.length && !line[end].isWhitespace()) end++
            val word = line.substring(idx, end)
            val plain = word.all { it.isLowerCase() || it == '\'' }
            if (plain) {
                out.add(line.substring(0, idx) + word[0].uppercaseChar() + line.substring(idx + 1))
            } else {
                out.add(line)
            }
        }
        return out.joinToString("\n")
    }
}
