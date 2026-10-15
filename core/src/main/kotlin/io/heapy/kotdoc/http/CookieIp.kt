// iproute-1.7.15 Read paths and appar-0.1.8 Text.Appar.Parser combinators.
// Copyright (c) 2009, IIJ Innovation Institute Inc. BSD-3-Clause.
// Full notices: licenses/iproute-BSD-3-Clause.txt and licenses/appar-BSD-3-Clause.txt.
package io.heapy.kotdoc.http

import io.heapy.kotdoc.parsing.ParserCharacters

internal object CookieIp {
    private data class Result<A : Any>(val value: A?, val at: Int)
    private class P<A : Any>(val run: (String, Int) -> Result<A>) {
        fun <B : Any> map(f: (A) -> B) = P<B> { s,i -> run(s,i).let { Result(it.value?.let(f),it.at) } }
        fun <B : Any> flat(f: (A) -> P<B>) = P<B> { s,i -> run(s,i).let { r ->
            if (r.value == null) Result(null,r.at) else f(r.value).run(s,r.at)
        } }
        // Appar restarts the alternative at the FAILED position, not the original.
        fun or(other: P<A>) = P<A> { s,i -> run(s,i).let { if (it.value == null) other.run(s,it.at) else it } }
        fun attempt() = P<A> { s,i -> run(s,i).let { if (it.value == null) Result(null,i) else it } }
    }
    private fun <A : Any> pure(value: A) = P<A> { _,i -> Result(value,i) }
    private fun <A : Any> failure() = P<A> { _,i -> Result(null,i) }
    private fun satisfy(test: (Char)->Boolean) = P<Char> { s,i -> if (i<s.length && test(s[i])) Result(s[i],i+1) else Result(null,i) }
    private fun char(c: Char) = satisfy { it==c }
    private fun string(s: String): P<String> = if (s.isEmpty()) pure("") else char(s.first()).flat { string(s.drop(1)).map { s } }
    private fun <A : Any> many(p: P<A>) = P<List<A>> { s,i ->
        val values=mutableListOf<A>();var at=i;var done=false
        while (!done) {
            val r=p.run(s,at);at=r.at
            if (r.value==null) done=true else values.add(r.value)
        }
        Result(values,at)
    }
    private fun <A : Any> some(p: P<A>): P<List<A>> = p.flat { head -> many(p).map { listOf(head)+it } }
    private fun <A : Any, B : Any> manyTill(p: P<A>, end: P<B>) = P<List<A>> { s,i ->
        val values=mutableListOf<A>();var at=i;var failed=false
        while (true) {
            val e=end.run(s,at);at=e.at
            if (e.value!=null) break
            val r=p.run(s,at);at=r.at
            if (r.value==null) { failed=true;break }
            values.add(r.value)
        }
        Result(if (failed) null else values,at)
    }
    private fun spaces() = many(char(' ')).map { Unit }
    private fun end() = P<Unit> { s,i -> Result(if(i==s.length) Unit else null,i) }
    private fun octet(): P<Int> = char('0').map { 0 }.or(
        satisfy { it in '1'..'9' }.flat { first -> many(satisfy { it in '0'..'9' }).flat { rest ->
            val value=(first+rest.joinToString("")).toIntOrNull()
            if (value!=null && value<=255) pure(value) else failure()
        } },
    )
    private fun ip4(): P<List<Int>> = octet().flat { a -> char('.').flat { octet().flat { b -> char('.').flat {
        octet().flat { c -> char('.').flat { octet().flat { d -> spaces().flat { end().map { listOf(a,b,c,d) } } } } }
    } } } }
    private fun hex(): P<Int> = some(satisfy { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }).flat {
        if (it.size>4) failure() else pure(it.joinToString("").toInt(16))
    }
    private fun hexColon(): P<List<Int>> = hex().flat { first -> many(char(':').flat { hex() }).map { listOf(first)+it } }
    private fun format(before: List<Int>, after: List<Int>): P<List<Int>> {
        val count=8-before.size-after.size
        return if (before.size>7 || after.size>7 || count<=0) failure() else pure(before+List(count) {0}+after)
    }
    private fun beforeEmbedded() = many(hex().flat { value -> char(':').map {value} }.attempt())
    private fun embeddedWords(ip: List<Int>) = listOf((ip[0] shl 8) or ip[1],(ip[2] shl 8) or ip[3])
    private fun embedded(): P<List<Int>> {
        val leading=string("::").flat { beforeEmbedded().flat { before -> ip4().flat { format(emptyList(),before+embeddedWords(it)) } } }.attempt()
        val compressed=manyTill(hex().flat { value->char(':').map {value} }.attempt(),char(':')).flat { before ->
            beforeEmbedded().or(pure(emptyList())).flat { after -> ip4().flat { format(before,after+embeddedWords(it)) } }
        }.attempt()
        val full=beforeEmbedded().flat { before -> ip4().flat { ip ->
            val values=before+embeddedWords(ip)
            if (values.size==8) pure(values) else failure()
        } }.attempt()
        return leading.or(compressed).or(full)
    }
    private fun ip6(): P<List<Int>> {
        val leading=string("::").flat { hexColon().or(pure(emptyList())).flat { format(emptyList(),it) } }
        val full=hexColon().flat { if(it.size==8) pure(it) else failure() }.attempt()
        val compressed=manyTill(hex().flat { value->char(':').map {value} },char(':')).flat { before ->
            hexColon().or(pure(emptyList())).flat { after -> format(before,after) }
        }
        return spaces().flat { embedded().or(leading).or(full).or(compressed) }
    }
    private fun readMaybe(input: String, p: P<List<Int>>): List<Int>? {
        val result=p.run(input,0)
        return result.value?.takeIf { input.substring(result.at).all { ParserCharacters.isSpace(it.code) } }
    }
    fun ipv4(input: String): List<Int>? = readMaybe(input,spaces().flat {ip4()})
    fun ipv6(input: String): List<Int>? = readMaybe(input,ip6())
}
