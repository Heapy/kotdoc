// Network.URI 2.6.4.2, (c) 2004 Graham Klyne. BSD-3-Clause;
// licenses/Network-uri-Graham-Klyne-BSD-3-Clause.txt.
package io.heapy.kotdoc.uri

import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.parsing.*

private typealias P<A> = Parsec<Unit, A>

/** Original Parsec grammar, retaining its bounded reads and alternative order. */
internal object URIParsing {
    private fun c(c: Char) = Parsec.char<Unit>(c.code)
    private fun text(s: String) = Parsec.string<Unit>(s)
    private fun sat(p: (Int) -> Boolean) = Parsec.satisfy<Unit>(p)
    private fun <A> pure(a: A) = Parsec.pure<Unit, A>(a)
    private fun <A> count(min: Int, max: Int, p: P<A>): P<List<A>> {
        if (min <= 0 && max <= 0) return pure(emptyList())
        val next = p.flatMap { a -> Parsec.defer { count((min - 1).coerceAtLeast(0), max - 1, p) }.map { listOf(a) + it } }
        return if (min > 0) next else next.option(emptyList())
    }
    private fun chars(p: P<Int>) = p.many().map { String(it.toIntArray(), 0, it.size) }
    private fun chunks(p: P<String>) = p.many().map { it.joinToString("") }
    private fun chunks1(p: P<String>) = p.many1().map { it.joinToString("") }
    private fun alpha(n: Int) = n in 65..90 || n in 97..122
    private fun digit(n: Int) = n in 48..57
    private fun hex(n: Int) = digit(n) || n in 65..70 || n in 97..102
    private fun schemeChar(n: Int) = alpha(n) || digit(n) || n in intArrayOf(43, 45, 46)
    private val escaped get() = c('%').flatMap { a -> sat(::hex).flatMap { b -> sat(::hex).map { d -> String(intArrayOf(a,b,d),0,3) } } }
    private val unreserved get() = sat(NetworkURI::isUnreserved).map { String(Character.toChars(it)) }
    private val sub get() = sat(NetworkURI::isSubDelims).map { String(Character.toChars(it)) }
    private fun uchar(extra: String): P<String> {
        val allowed = extra.codePoints().toArray()
        return unreserved or escaped or sub or sat { it in allowed }.map { String(Character.toChars(it)) }
    }
    private val scheme get() = sat(::alpha).flatMap { first -> chars(sat(::schemeChar)).before(c(':')).map { first.toChar() + it + ":" } }
    private val name get() = (unreserved or escaped or sub).label("Name character")
    private val regName get() = count(0,255,name).map { it.joinToString("") }.label("Registered name")
    private val userInfo get() = chunks(uchar(";:&=+$,")).before(c('@')).map { it + "@" }
    private val port get() = c(':').then(chars(sat(::digit))).map { ":$it" }
    private val decOctet get() = count(1,3,sat(::digit)).flatMap { xs ->
        val s = String(xs.toIntArray(),0,xs.size)
        if (s.toInt() > 255) Parsec.fail("Decimal octet value too large") else pure(s)
    }
    private val ipv4 get() = decOctet.flatMap { a -> c('.').then(decOctet).flatMap { b ->
        c('.').then(decOctet).flatMap { d -> c('.').then(decOctet).before(name.notFollowedBy { it }).map { e -> "$a.$b.$d.$e" } }
    } }.label("IPv4 Address")
    private val h4 get() = count(1,4,sat(::hex)).map { String(it.toIntArray(),0,it.size) }
    private val h4c get() = h4.before(c(':')).before(c(':').notFollowedBy(Parsec.Companion::showChar)).map { "$it:" }.attempt()
    private val ls32 get() = h4c.flatMap { a -> h4.map { a + it } }.attempt() or ipv4
    private fun opt(n: Int) = count(0,n,h4c).flatMap { p -> h4.map { p.joinToString("") + it } }.option("")
    private val ipv6: P<String> get() {
        val variants = mutableListOf(count(6,6,h4c).flatMap { p -> ls32.map { p.joinToString("") + it } }.attempt())
        variants.add(text("::").then(count(5,5,h4c)).flatMap { p -> ls32.map { "::" + p.joinToString("") + it } }.attempt())
        for (n in 0..4) variants.add(opt(n).flatMap { first -> text("::").then(count(4-n,4-n,h4c)).flatMap { p -> ls32.map { first + "::" + p.joinToString("") + it } } }.attempt())
        variants.add(opt(5).flatMap { p -> text("::").then(h4).map { p + "::" + it } }.attempt())
        variants.add(opt(6).before(text("::")).map { "$it::" }.attempt())
        return variants.reduce { a,b -> a or b }.label("IPv6 address")
    }
    private val zone get() = text("%25").flatMap { p -> chunks1(unreserved or escaped).map { p + it } }.attempt().option("")
    private val ipvFuture get() = c('v').then(sat(::hex)).flatMap { h -> c('.').then(sat { NetworkURI.isUnreserved(it) || NetworkURI.isSubDelims(it) }.many1()).map { "v" + h.toChar() + "." + String(it.toIntArray(),0,it.size) } }
    private val literal get() = c('[').then((ipv6.flatMap { p -> zone.map { p + it } }) or ipvFuture).before(c(']')).map { "[$it]" }.label("IP address literal")
    private val host get() = literal or ipv4.attempt() or regName
    private val authority get() = userInfo.attempt().option("").flatMap { u -> host.flatMap { h -> port.option("").map { URIAuth(u,h,it) } } }
    private val segment get() = chunks(uchar(":@"))
    private val slashSegment get() = c('/').then(segment).map { "/$it" }
    private fun rootless(noScheme: Boolean) = chunks1(uchar(if(noScheme) "@" else ":@")).flatMap { p -> chunks(slashSegment).map { p + it } }
    private fun part(relative: Boolean): P<Pair<URIAuth?,String>> =
        text("//").attempt().then(authority).flatMap { a -> chunks(slashSegment).map<Pair<URIAuth?,String>> { a to it } } or
        c('/').then(rootless(false).option("")).map<Pair<URIAuth?,String>> { null to "/$it" } or rootless(relative).map<Pair<URIAuth?,String>> { null to it } or pure<Pair<URIAuth?,String>>(null to "")
    private fun tail(prefix: Char) = c(prefix).then(chunks(uchar(":@/?"))).map { prefix + it }.option("")
    private fun absolute(fragment: Boolean, tried: Boolean): P<URI> = (if(tried) scheme.attempt() else scheme).flatMap { s ->
        part(false).flatMap { (a,p) -> tail('?').flatMap { q -> (if(fragment) tail('#') else pure("")).map { f -> URI(s,a,p,q,f) } } }
    }
    private val relative get() = (scheme.attempt().flatMap { Parsec.unexpected<Unit,Unit>(it) } or pure(Unit)).then(
        part(true).flatMap { (a,p) -> tail('?').flatMap { q -> tail('#').map { f -> URI("",a,p,q,f) } } })
    // Original top-level parser values are shared between calls. Runtime frames
    // and input/user state still belong to each independent run.
    private val parsers by lazy { listOf(absolute(true,true), absolute(true,true) or relative, relative, absolute(false,false)) }
    private val ipv4Parser by lazy { ipv4 }
    private val ipv6Parser by lazy { ipv6 }
    fun parse(input: CharacterString, kind: Int): URI? = run(parsers[kind],input)
    fun ip(input: CharacterString, version: Int) = run(if(version==4) ipv4Parser else ipv6Parser,input) != null
    private fun <A> run(p: P<A>, input: CharacterString): A? {
        val r = p.before(Parsec.eof()).run(ParseState(ParseInput(CharacterListStream(input.points())),SourcePos(""),Unit))
        return if(r.success) r.value else null
    }
}
