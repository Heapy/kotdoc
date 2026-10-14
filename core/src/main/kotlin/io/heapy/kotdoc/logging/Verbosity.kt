// Text.Pandoc.Logging and GHC derived Enum/Read; original notices in licenses/.
package io.heapy.kotdoc.logging

import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.parsing.ParserCharacters
import kotlinx.serialization.json.*

enum class Verbosity {
    ERROR, WARNING, INFO;
    fun toJson(): JsonElement = JsonPrimitive(name)
    fun successor(): Verbosity {
        require(this!=INFO) { "Enum.succ{Verbosity}: tried to take `succ' of last tag in enumeration" }
        return entries[ordinal+1]
    }
    fun predecessor(): Verbosity {
        require(this!=ERROR) { "Enum.pred{Verbosity}: tried to take `pred' of first tag in enumeration" }
        return entries[ordinal-1]
    }
    fun enumerate(then: Verbosity? = null,to: Verbosity? = null): Sequence<Verbosity> = sequence {
        val step=then?.let { it.ordinal-ordinal } ?: 1
        val limit=to?.ordinal ?: if(step<0) 0 else 2
        var i=ordinal
        while(if(step<0) i>=limit else i<=limit) { yield(entries[i]);i+=step }
    }
    companion object {
        val minimum get() = ERROR
        val maximum get() = INFO
        fun fromOrdinal(tag: Long): Verbosity {
            require(tag in 0L..2L) { "Enum.toEnum{Verbosity}: tag ($tag) is outside of enumeration's range (0,2)" }
            return entries[tag.toInt()]
        }
        fun fromJson(value: JsonElement): Verbosity = entries.firstOrNull {
            value is JsonPrimitive && value.isString && value.content==it.name
        } ?: throw IllegalArgumentException("mzero")

        /** GHC's cut-down Read lexer skips whitespace, not Haskell source comments. */
        fun reads(input: CharacterString,precedence: Long = 0): List<Pair<Verbosity,CharacterString>> {
            @Suppress("UNUSED_VARIABLE") val sourcePrecedence=precedence
            val chars=input.points();var at=0;var parentheses=0
            fun spaces() { while(at<chars.size && ParserCharacters.isSpace(chars[at])) at++ }
            spaces()
            while(at<chars.size && chars[at]==40) { parentheses++;at++;spaces() }
            val start=at
            if(at>=chars.size || !(ParserCharacters.isAlpha(chars[at]) || chars[at]==95)) return emptyList()
            at++
            while(at<chars.size && (ParserCharacters.isAlphaNum(chars[at]) || chars[at] in intArrayOf(95,39))) at++
            val word=String(chars,start,at-start)
            val value=entries.firstOrNull { it.name==word } ?: return emptyList()
            repeat(parentheses) { spaces();if(at>=chars.size || chars[at]!=41) return emptyList();at++ }
            return listOf(value to CharacterString(chars.copyOfRange(at,chars.size)))
        }
        fun reads(input: String,precedence: Long = 0)=reads(CharacterStringChars.fromString(input),precedence)
        fun read(input: String): Verbosity {
            val values=reads(input).filter { (_,rest) -> rest.points().all(ParserCharacters::isSpace) }
            require(values.size==1) { if(values.isEmpty()) "Prelude.read: no parse" else "Prelude.read: ambiguous parse" }
            return values.single().first
        }
    }
}
