// Port of Pandoc Text.Pandoc.Readers.Native and Shared.safeStrRead.
// Copyright (C) 2011-2024 John MacFarlane. GPL-2.0-or-later; see licenses/.
package io.heapy.kotdoc.formats

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.parsing.ParserCharacters

/** Reads Pandoc, [Block], Block, [Inline], or Inline in the original fallback order. */
object NativeReader {
    private fun <A> complete(result:Pair<A,CharacterString>?):A? =
        result?.takeIf { it.second.points().all(ParserCharacters::isSpace) }?.first

    fun read(input:String):Pandoc {
        // ToSources Text removes raw CR before any Read dictionary sees input.
        val text=input.filterNot { it=='\r' }
        complete(PandocRead.Pandoc.readFirst(text))?.let { return it }
        complete(PandocRead.Block.readListFirst(text))?.let { return Pandoc(blocks=it) }
        complete(PandocRead.Block.readFirst(text))?.let { return Pandoc(blocks=listOf(it)) }
        complete(PandocRead.Inline.readListFirst(text))?.let { return Pandoc(blocks=listOf(Block.Plain(it))) }
        complete(PandocRead.Inline.readFirst(text))?.let { return Pandoc(blocks=listOf(Block.Plain(listOf(it)))) }
        throw IllegalArgumentException("couldn't read native")
    }
}
