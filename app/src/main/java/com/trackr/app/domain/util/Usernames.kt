package com.trackr.app.domain.util

/**
 * The username rule, shared by the username box, the save check and the server's constraint
 * (`^[A-Za-z0-9_.]{3,24}$` on profiles.username): English letters, digits, `.` and `_`, 3 to 24 of them.
 */
object Usernames {
    const val MIN = 3
    const val MAX = 24
    const val HINT = "3–24 English letters, numbers, . or _"

    private val pattern = Regex("^[A-Za-z0-9_.]{$MIN,$MAX}$")

    fun isValid(name: String): Boolean = pattern.matches(name)

    /**
     * What the username box keeps of typed text: spaces become `_`, anything else outside the rule is dropped (accented
     * or non-Latin letters included, which the server would refuse), cut to [MAX].
     */
    fun clean(input: String): String =
        input.map { if (it == ' ') '_' else it }.filter { it.isAllowed() }.take(MAX).joinToString("")

    private fun Char.isAllowed() = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this == '_' || this == '.'
}
