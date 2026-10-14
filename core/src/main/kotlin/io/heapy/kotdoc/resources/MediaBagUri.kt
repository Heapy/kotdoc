// network-uri 2.6.4.2 Network.URI absolute URI grammar used by Text.Pandoc.MediaBag.
// Original BSD-3-Clause source/license retained under vendor/ and licenses/.
package io.heapy.kotdoc.resources

/** Source grammar projection, including its IPvFuture and reg-name limits. */
internal object MediaBagUri {
    data class Parsed(val scheme: String, val path: String)
    private val schemes = checkNotNull(javaClass.getResource("/io/heapy/kotdoc/uri-schemes.txt")).readText().lines().toSet()
    private fun alpha(c: Char) = c in 'A'..'Z' || c in 'a'..'z'
    private fun digit(c: Char) = c in '0'..'9'
    private fun hex(c: Char) = digit(c) || c in 'a'..'f' || c in 'A'..'F'
    private fun unreserved(c: Char) = alpha(c) || digit(c) || c in "-_.~"
    private fun sub(c: Char) = c in "!$&'()*+,;="
    private fun tokens(s: String, allowed: (Char) -> Boolean): Int? {
        var at=0;var count=0
        while(at<s.length) {
            val c=s[at]
            if(c=='%') {
                if(at+2>=s.length || !hex(s[at+1]) || !hex(s[at+2])) return null
                at+=3
            } else {
                if(!allowed(c)) return null
                at++
            }
            count++
        }
        return count
    }
    fun parse(s: String): Parsed? {
        if(s.any { it.code>=128 }) return null
        val colon=s.indexOf(':')
        if(colon<=0 || !alpha(s[0]) || !s.substring(1,colon).all { alpha(it)||digit(it)||it in "+-." }) return null
        val scheme=s.substring(0,colon)
        var body=s.substring(colon+1)
        val fragment=body.indexOf('#')
        if(fragment>=0) {
            if(tokens(body.substring(fragment+1)) { unreserved(it)||sub(it)||it in ":@/?" }==null) return null
            body=body.substring(0,fragment)
        }
        val query=body.indexOf('?')
        if(query>=0) {
            if(tokens(body.substring(query+1)) { unreserved(it)||sub(it)||it in ":@/?" }==null) return null
            body=body.substring(0,query)
        }
        val path=if(body.startsWith("//")) {
            val end=body.indexOf('/',2).let { if(it<0) body.length else it }
            if(!authority(body.substring(2,end))) return null
            body.substring(end)
        } else body
        if(tokens(path) { unreserved(it)||sub(it)||it in ":@/" }==null) return null
        return Parsed(scheme,path)
    }
    fun isKnown(s: String): Boolean {
        val escaped=buildString {
            s.codePoints().forEach { cp ->
                if(cp<128) appendCodePoint(cp) else for(b in String(Character.toChars(cp)).toByteArray(Charsets.UTF_8))
                    append('%').append((b.toInt() and 255).toString(16).uppercase().padStart(2,'0'))
            }
        }
        return parse(escaped)?.scheme?.lowercase() in schemes
    }
    private fun authority(s: String): Boolean {
        val at=s.indexOf('@')
        val hostport=if(at>=0) {
            if(tokens(s.substring(0,at)) { unreserved(it)||sub(it)||it==':' }==null) return false
            s.substring(at+1)
        } else s
        if(hostport.startsWith('[')) {
            val end=hostport.indexOf(']');if(end<0) return false
            if(!literal(hostport.substring(1,end))) return false
            val suffix=hostport.substring(end+1)
            return suffix.isEmpty() || suffix.startsWith(':') && suffix.substring(1).all(::digit)
        }
        val colon=hostport.indexOf(':')
        val host=if(colon<0) hostport else hostport.substring(0,colon)
        if(colon>=0 && !hostport.substring(colon+1).all(::digit)) return false
        val count=tokens(host) { unreserved(it)||sub(it) } ?: return false
        return count<=255
    }
    private fun literal(s: String): Boolean {
        // The pinned source accepts one hex digit after lowercase v, and its
        // isIpvFutureChar omits colon. Preserve those source choices.
        if(s.startsWith('v')) return s.length>=4 && hex(s[1]) && s[2]=='.' && s.substring(3).all { unreserved(it)||sub(it) }
        val zone=s.indexOf('%')
        val ip=if(zone<0) s else {
            if(!s.startsWith("%25",zone)) return false
            val z=s.substring(zone+3)
            if(z.isEmpty() || tokens(z,::unreserved)==null) return false
            s.substring(0,zone)
        }
        val compression=ip.indexOf("::")
        if(compression>=0 && ip.indexOf("::",compression+2)>=0) return false
        val parts=if(compression<0) ip.split(':') else {
            val l=ip.substring(0,compression);val r=ip.substring(compression+2)
            (if(l.isEmpty()) emptyList() else l.split(':')) + (if(r.isEmpty()) emptyList() else r.split(':'))
        }
        var groups=0
        for((i,p) in parts.withIndex()) {
            if('.' in p) {
                if(i!=parts.lastIndex || !ipv4(p)) return false
                groups+=2
            } else {
                if(p.length !in 1..4 || !p.all(::hex)) return false
                groups++
            }
        }
        return if(compression<0) groups==8 else groups<8
    }
    private fun ipv4(s: String): Boolean {
        val parts=s.split('.')
        return parts.size==4 && parts.all { it.length in 1..3 && it.all(::digit) && it.toInt()<=255 }
    }
}
