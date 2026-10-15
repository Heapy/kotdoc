// Network.URI 2.6.4.2, (c) 2004 Graham Klyne. BSD-3-Clause;
// licenses/Network-uri-Graham-Klyne-BSD-3-Clause.txt.
// UTF-8 routines adapted upstream from Eric Mertens' utf8-string, BSD-3-Clause.
package io.heapy.kotdoc.uri

import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars as Chars
import io.heapy.kotdoc.parsing.RegexCharacters

/** Runtime exports of the pinned Network.URI; String facades retain raw Char APIs. */
object NetworkURI {
    val nullURI get() = URI.empty
    val nullURIAuth get() = URIAuth.empty
    fun parseURI(s: CharacterString) = URIParsing.parse(s,0)
    fun parseURIReference(s: CharacterString) = URIParsing.parse(s,1)
    fun parseRelativeReference(s: CharacterString) = URIParsing.parse(s,2)
    fun parseAbsoluteURI(s: CharacterString) = URIParsing.parse(s,3)
    fun parseURI(s: String) = parseURI(Chars.fromString(s))
    fun parseURIReference(s: String) = parseURIReference(Chars.fromString(s))
    fun parseRelativeReference(s: String) = parseRelativeReference(Chars.fromString(s))
    fun parseAbsoluteURI(s: String) = parseAbsoluteURI(Chars.fromString(s))
    fun isURI(s: CharacterString) = parseURI(s) != null
    fun isURIReference(s: CharacterString) = parseURIReference(s) != null
    fun isRelativeReference(s: CharacterString) = parseRelativeReference(s) != null
    fun isAbsoluteURI(s: CharacterString) = parseAbsoluteURI(s) != null
    fun isIPv4address(s: CharacterString) = URIParsing.ip(s,4)
    fun isIPv6address(s: CharacterString) = URIParsing.ip(s,6)
    fun uriIsAbsolute(u: URI) = u.isAbsolute()
    fun uriIsRelative(u: URI) = u.isRelative()
    fun rectify(u: URI) = u.rectify()
    fun rectifyAuth(a: URIAuth) = a.rectify()
    fun uriToString(map: (CharacterString)->CharacterString,u: URI,suffix: CharacterString = Chars.empty) = u.uriToString(map,suffix)
    fun uriAuthToString(map: (CharacterString)->CharacterString,a: URIAuth?,suffix: CharacterString = Chars.empty) = io.heapy.kotdoc.uri.uriAuthToString(map,a,suffix)
    internal fun isSubDelims(c: Int) = when(c) { 33,36,38,39,40,41,42,43,44,59,61 -> true;else -> false }
    fun isReserved(c: Int) = when(c) { 58,47,63,35,91,93,64 -> true;else -> isSubDelims(c) }
    fun isUnreserved(c: Int) = c in 65..90 || c in 97..122 || c in 48..57 || c==45 || c==95 || c==46 || c==126
    fun isAllowedInURI(c: Int) = isReserved(c) || isUnreserved(c) || c==37
    fun isUnescapedInURI(c: Int) = isReserved(c) || isUnreserved(c)
    fun isUnescapedInURIComponent(c: Int) = !isReserved(c) && isUnescapedInURI(c)
    private fun hex(c: Int) = when(c) { in 48..57 -> c-48; in 65..70 -> c-55; in 97..102 -> c-87; else -> -1 }
    fun escapeURIChar(predicate: (Int)->Boolean,c: Int): CharacterString {
        require(c in 0..0x10ffff)
        if(predicate(c)) return CharacterString(intArrayOf(c))
        val bytes = when {
            c<=0x7f -> intArrayOf(c)
            c<=0x7ff -> intArrayOf(0xc0+(c ushr 6),0x80+(c and 63))
            c<=0xffff -> intArrayOf(0xe0+(c ushr 12),0x80+((c ushr 6) and 63),0x80+(c and 63))
            else -> intArrayOf(0xf0+(c ushr 18),0x80+((c ushr 12) and 63),0x80+((c ushr 6) and 63),0x80+(c and 63))
        }
        val digits="0123456789ABCDEF"
        return CharacterString(bytes.flatMap { listOf(37,digits[it ushr 4].code,digits[it and 15].code) }.toIntArray())
    }
    fun escapeURIString(predicate: (Int)->Boolean,s: CharacterString) = Chars.concat(s.points().map { escapeURIChar(predicate,it) })
    fun unEscapeString(s: CharacterString): CharacterString {
        val p=s.points();val out=ArrayList<Int>();var at=0
        fun byte(): Int? = if(at+2<p.size && p[at]==37 && hex(p[at+1])>=0 && hex(p[at+2])>=0) hex(p[at+1])*16+hex(p[at+2]) else null
        while(at<p.size) {
            val b=byte();if(b==null) { out.add(p[at++]);continue };at+=3
            if(b<0x80) { out.add(b);continue }
            if(b<0xc0 || b>=0xfe) { out.add(0xfffd);continue }
            val count=when { b<0xe0->1;b<0xf0->2;b<0xf8->3;b<0xfc->4;else->5 }
            val mask=when(count) { 1->31;2->15;3->7;4->3;else->1 }
            val min=when(count) { 1->0x80;2->0x800;3->0x10000;4->0x200000;else->0x4000000 }
            var value=b and mask;var read=0
            while(read<count) { val next=byte() ?: break;if(next and 0xc0 != 0x80) break;at+=3;read++;value=(value shl 6) or (next and 63) }
            val valid=read==count && value>=min && (count==1 || value<=0x10ffff && value !in 0xd800..0xdfff && value !in 0xfffe..0xffff)
            out.add(if(valid) value else 0xfffd)
        }
        return CharacterString(out.toIntArray())
    }
    fun normalizeCase(s: CharacterString): CharacterString {
        val p=s.points();var end=0
        fun schemeChar(c: Int) = c in 65..90 || c in 97..122 || c in 48..57 || c in intArrayOf(43,45,46)
        while(end<p.size && p[end]!=58 && schemeChar(p[end])) end++
        val prefix=IntArray(end) { RegexCharacters.toLower(p[it]) }
        // ncScheme keeps its recursively emitted prefix even when its fallback
        // calls ncEscape on the complete original argument (for example g -> gg).
        val rest=p.copyOfRange(if(end<p.size && p[end]==58) end else 0,p.size)
        var at=0
        while(at<rest.size) { if(rest[at]==37 && at+2<rest.size) { rest[at+1]=RegexCharacters.toUpper(rest[at+1]);rest[at+2]=RegexCharacters.toUpper(rest[at+2]);at+=3 } else at++ }
        return CharacterString(prefix+rest)
    }
    fun normalizeEscape(s: CharacterString): CharacterString {
        val p=s.points();val out=ArrayList<Int>();var at=0
        while(at<p.size) {
            if(at+2<p.size && p[at]==37 && hex(p[at+1])>=0 && hex(p[at+2])>=0) {
                val c=hex(p[at+1])*16+hex(p[at+2]);if(isUnreserved(c)) { out.add(c);at+=3;continue }
            }
            out.add(p[at++])
        }
        return CharacterString(out.toIntArray())
    }
    fun normalizePathSegments(s: CharacterString): CharacterString = parseURI(s)?.let { it.copy(path=removeDots(it.path)).showCharacters() } ?: s
    fun relativeTo(ref: URI,base: URI): URI {
        val path=ref.path.points();val basePath=base.path.points()
        val u=when {
            ref.scheme.points().isNotEmpty() -> ref
            ref.authority!=null -> ref.copy(scheme=base.scheme)
            path.isNotEmpty() -> ref.copy(scheme=base.scheme,authority=base.authority,path=if(path[0]==47)ref.path else {
                val prefix=if(base.authority!=null && basePath.isEmpty()) intArrayOf(47) else basePath.copyOfRange(0,basePath.indexOfLast { it==47 }+1)
                CharacterString(prefix+path)
            })
            ref.query.points().isNotEmpty() -> ref.copy(scheme=base.scheme,authority=base.authority,path=base.path)
            else -> ref.copy(scheme=base.scheme,authority=base.authority,path=base.path,query=base.query)
        }
        return u.copy(path=removeDots(u.path))
    }
    fun nonStrictRelativeTo(ref: URI,base: URI) = relativeTo(if(ref.scheme==base.scheme) ref.copy(scheme=Chars.empty) else ref,base)
    fun pathSegments(u: URI): List<CharacterString> {
        val p=u.path.points();val out=ArrayList<CharacterString>();var at=0
        while(at<p.size) { val start=at;while(at<p.size && p[at]!=47)at++;out.add(CharacterString(p.copyOfRange(start,at)));if(at<p.size)at++ }
        if(out.firstOrNull()?.points()?.isEmpty()==true)out.removeAt(0)
        return out
    }
    private fun removeDots(s: CharacterString): CharacterString {
        val p=s.points();val rooted=p.firstOrNull()==47;var at=if(rooted)1 else 0;val segments=ArrayList<Pair<Int,Int>>()
        while(at<p.size) {
            if(p[at]==46 && (at+1==p.size || p[at+1]==47)) { at+=if(at+1==p.size)1 else 2;continue }
            if(p[at]==46 && at+1<p.size && p[at+1]==46 && (at+2==p.size || p[at+2]==47)) { if(segments.isNotEmpty())segments.removeAt(segments.lastIndex);at+=if(at+2==p.size)2 else 3;continue }
            val start=at;while(at<p.size && p[at]!=47)at++;if(at<p.size)at++;segments.add(start to at)
        }
        return CharacterString(buildList { if(rooted)add(47);for((a,b) in segments)for(i in a..<b)add(p[i]) }.toIntArray())
    }
    private class Part(val data: IntArray,val from: Int=0,val until: Int=data.size) {
        val empty get()=from==until
        fun bytes()=data.copyOfRange(from,until)
        fun split(): Pair<Part,Part> { var end=from;while(end<until && data[end]!=47)end++;if(end<until)end++;return Part(data,from,end) to Part(data,end,until) }
        fun last(): Pair<Part,Part> { var at=until;while(at>from && data[at-1]!=47)at--;return Part(data,from,at) to Part(data,at,until) }
        fun same(other: Part): Boolean = until-from==other.until-other.from && (0..<until-from).all { data[from+it]==other.data[other.from+it] }
    }
    fun relativeFrom(absolute: URI,base: URI): URI = when {
        absolute.scheme!=base.scheme -> absolute
        absolute.authority!=base.authority -> absolute.copy(scheme=Chars.empty)
        absolute.path!=base.path -> absolute.copy(scheme=Chars.empty,authority=null,path=relativePath(bodyDots(absolute.path),bodyDots(base.path)))
        absolute.query!=base.query -> absolute.copy(scheme=Chars.empty,authority=null,path=Chars.empty)
        else -> absolute.copy(scheme=Chars.empty,authority=null,path=Chars.empty,query=Chars.empty)
    }
    private fun bodyDots(s: CharacterString): CharacterString { val(p,n)=Part(s.points()).last();return Chars.append(removeDots(CharacterString(p.bytes())),CharacterString(n.bytes())) }
    private fun relativePath(target: CharacterString,base: CharacterString): CharacterString {
        val a=Part(target.points());val b=Part(base.points());if(a.empty)return Chars.fromString("/");if(b.empty)return target
        val(sa,ra)=a.split();val(sb,rb)=b.split();if(!sa.same(sb))return target
        val aa:Part;val bb:Part
        if(sa.bytes().contentEquals(intArrayOf(47))) { val(sa2,ra2)=ra.split();val(sb2,rb2)=rb.split();if(!sa2.same(sb2))return target;aa=ra2;bb=rb2 } else { aa=ra;bb=rb }
        val(ad,an)=aa.last();val(bd,bn)=bb.last();var x=ad;var y=bd
        while(!x.empty || !y.empty) { val(sx,rx)=x.split();val(sy,ry)=y.split();if(!sx.same(sy))break;x=rx;y=ry }
        var ups=0;while(!y.empty) { ups++;y=y.split().second }
        val rel=IntArray(ups*3) { when(it%3){0,1->46;else->47} }+x.bytes()
        val name=an.bytes()
        return CharacterString(if(rel.isEmpty()) { if(an.same(bn))intArrayOf() else if(name.isEmpty() || 58 in name)intArrayOf(46,47)+name else name } else rel+name)
    }
    fun parseabsoluteURI(s: CharacterString) = parseAbsoluteURI(s)
    fun escapeString(s: CharacterString,p: (Int)->Boolean) = escapeURIString(p,s)
    fun reserved(c: Int) = isReserved(c)
    fun unreserved(c: Int) = isUnreserved(c)
    fun scheme(u: URI) = u.scheme.points().let { CharacterString(if(it.isEmpty())it else it.copyOf(it.size-1)) }
    fun authority(u: URI): CharacterString = uriAuthToString({it},u.authority).points().let { CharacterString(if(it.size>=2 && it[0]==47 && it[1]==47)it.copyOfRange(2,it.size) else it) }
    fun path(u: URI) = u.path
    fun query(u: URI) = dropFirst(u.query)
    fun fragment(u: URI) = dropFirst(u.fragment)
    private fun dropFirst(s: CharacterString) = s.points().let { CharacterString(if(it.isEmpty())it else it.copyOfRange(1,it.size)) }
}
