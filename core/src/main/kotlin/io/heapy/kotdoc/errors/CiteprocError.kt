// Citeproc.Types 0.14.1, © 2020 John MacFarlane. BSD-2-Clause, licenses/.
package io.heapy.kotdoc.errors

import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.layout.TextChars

sealed class CiteprocError(message: String) {
    val message = TextChars.fromString(message)
    class CiteprocXMLError(message: String) : CiteprocError(message)
    class CiteprocParseError(message: String) : CiteprocError(message)
    class CiteprocLocaleNotFound(message: String) : CiteprocError(message)
    val name get() = when (this) {
        is CiteprocXMLError -> "CiteprocXMLError"
        is CiteprocParseError -> "CiteprocParseError"
        is CiteprocLocaleNotFound -> "CiteprocLocaleNotFound"
    }
    fun pretty() = "$name: $message"
    fun show(precedence: Long = 0): String {
        val body = "$name ${HaskellString.quote(message)}"
        return if (precedence > 10) "($body)" else body
    }
    override fun toString() = show()
    override fun equals(other: Any?) = other is CiteprocError && name == other.name && message == other.message
    override fun hashCode() = 31 * name.hashCode() + message.hashCode()
}
