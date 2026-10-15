// Attoparsec 0.14.4 IResult/feed semantics; Bryan O'Sullivan and contributors.
// BSD-3-Clause; licenses/Attoparsec-BSD-3-Clause.txt.
package io.heapy.kotdoc.parsing.attoparsec

import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars as Chars
import io.heapy.kotdoc.parsing.Deferred

sealed class AttoResult<out A> {
    class Done<A> internal constructor(internal val buffer: TextRope,internal val position: Int,val deferred: Deferred<A>): AttoResult<A>() {
        val remaining: String get()=buffer.slice(position)
        val value: A get()=deferred.value()
    }
    class Fail internal constructor(internal val buffer: TextRope,internal val position: Int,val contexts: List<CharacterString>,val message: CharacterString): AttoResult<Nothing>() {
        val remaining: String get()=buffer.slice(position)
        val formattedCharacters: CharacterString get() = if(contexts.isEmpty())message else Chars.concat(buildList {
            contexts.forEachIndexed { i,c->if(i>0)add(Chars.fromString(" > "));add(c) }
            add(Chars.fromString(": "));add(message)
        })
        val formatted: String get()=Chars.build(formattedCharacters)
    }
    class Partial<A> internal constructor(private val continuation: (String)->AttoResult<A>): AttoResult<A>() {
        internal fun resume(chunk: String)=continuation(chunk)
    }
    fun feed(chunk: String): AttoResult<A> = when(this) {
        is Done->Done(buffer.append(chunk),position,deferred)
        is Fail->Fail(buffer.append(chunk),position,contexts,message)
        is Partial->resume(chunk)
    }
}

data class AttoMatch<out A>(val text: Deferred<String>,val parsed: Deferred<A>)
