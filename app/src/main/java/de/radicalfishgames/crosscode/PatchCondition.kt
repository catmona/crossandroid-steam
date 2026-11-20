package de.radicalfishgames.crosscode

/**
 * Evaluates the JS `cond` of an IF patch step natively, for the form mods use to check for other
 * mods: `activeMods.find(e => e.name === 'id')` combined with `!`, `&&`, `||` and brackets.
 * Returns null for anything else (e.g. localStorage checks), which the caller treats as unknown.
 */
object PatchCondition {
    private val modCheck =
        Regex("""activeMods\s*\.\s*(?:find|some)\s*\(\s*(\w+)\s*=>\s*\1\s*\.\s*name\s*===?\s*(['"])([^'"]*)\2\s*\)""")

    fun evaluate(
        cond: String,
        activeMods: Set<String>,
    ): Boolean? =
        try {
            val parser = Parser(cond, activeMods)
            val result = parser.or()
            parser.skipSpace()
            if (parser.pos == cond.length) result else null
        } catch (e: IllegalArgumentException) {
            null
        }

    private class Parser(
        val text: String,
        val activeMods: Set<String>,
    ) {
        var pos = 0

        fun skipSpace() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        private fun take(token: String): Boolean {
            skipSpace()
            if (text.startsWith(token, pos)) {
                pos += token.length
                return true
            }
            return false
        }

        fun or(): Boolean {
            var value = and()
            while (take("||")) value = and() || value
            return value
        }

        private fun and(): Boolean {
            var value = unary()
            while (take("&&")) value = unary() && value
            return value
        }

        private fun unary(): Boolean {
            if (take("!")) return !unary()
            if (take("(")) {
                val value = or()
                require(take(")")) { "missing )" }
                return value
            }
            skipSpace()
            val match = modCheck.matchAt(text, pos) ?: throw IllegalArgumentException("unsupported at $pos")
            pos = match.range.last + 1
            return match.groupValues[3] in activeMods
        }
    }
}
