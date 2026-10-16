// Data.X509.Ext general names and DN parsing, crypton-x509 1.9.2.
// Vincent Hanquez; licenses/crypton-x509-BSD-3-Clause.txt.
package io.heapy.kotdoc.x509

import io.heapy.kotdoc.asn1.*
import io.heapy.kotdoc.layout.CharacterString
import java.math.BigInteger

internal fun x509Oid(vararg arcs: Long)=arcs.map(BigInteger::valueOf)
internal val x509Seq=Asn1ConstructionType.Sequence
internal fun x509Context(n: Long)=Asn1ConstructionType.Container(Asn1Class.Context,n)
internal fun x509EncodeAlt(name: X509AltName): Sequence<Asn1Value> = sequence {
    when(name) {
        is X509AltName.DN -> yield(Asn1Value.Other.deferred(Asn1Class.Context,4) {Asn1BinaryEncoding().encodeDer(X509DistinguishedNameCodec.toAsn1(name.value)(emptyList()))})
        is X509AltName.IP -> yield(Asn1Value.Other.deferred(Asn1Class.Context,7) {name.bytes})
        is X509AltName.Text -> when(name.type) {
            X509TextNameKind.RFC822,X509TextNameKind.DNS,X509TextNameKind.URI -> {
                val tag=when(name.type) {X509TextNameKind.RFC822->1L;X509TextNameKind.DNS->2L;else->6L}
                yield(Asn1Value.Other.deferred(Asn1Class.Context,tag) {name.text.points().map {it.toByte()}.toByteArray()})
            }
            X509TextNameKind.XMPP,X509TextNameKind.DNSSRV -> {
                yield(Asn1Value.Start(x509Context(0)));yield(Asn1Value.Oid(x509Oid(1,3,6,1,5,5,7,8,5)))
                yield(Asn1Value.Start(x509Context(0)))
                yield(Asn1Value.StringValue(Asn1CharacterString.deferred(Asn1StringEncoding.UTF8) {Asn1CharacterString.encode(Asn1StringEncoding.UTF8,name.text).rawData}))
                yield(Asn1Value.End(x509Context(0)));yield(Asn1Value.End(x509Context(0)))
            }
        }
    }
}
private fun dnAttribute(input: X509Input): Asn1ParseResult<X509Read<X509DnAttribute>> = x509Container(input,x509Seq) {s ->
    val oid=s[0] as? Asn1Value.Oid
    val cs=if(oid!=null) s[1] as? Asn1Value.StringValue else null
    if(oid!=null && cs!=null && s[2]==null) x509Read({X509DnAttribute.deferred({oid.arcs},cs.value)},s.drop(2))
    else x509FailureLazy {"expecting [OID,String] got ${x509List(s.sequence())}"}
}
private fun dnSet(input: X509Input)=x509Container(input,Asn1ConstructionType.Set) {x509Many(it,::dnAttribute)}
internal fun x509DnFromInput(input: X509Input): Asn1ParseResult<X509Read<X509DistinguishedName>> {
    val result=x509Container(input,x509Seq) {x509Many(it,::dnSet)}
    return when(result) {
        is Asn1ParseResult.Failure -> result
        is Asn1ParseResult.Success -> x509Read({X509DistinguishedName.deferred {result.value.value.value.flatMap {group -> group.value.map {it.value}}}},result.value.rest)
    }
}
private fun composed(input: X509Input): Asn1ParseResult<X509Read<X509AltName>> {
    val n=input[0] ?: return x509Failure("empty")
    if(n !is Asn1Value.Oid) return x509FailureLazy {"GeneralNames: expecting OID but got $n"}
    val kind=when(n.arcs) {x509Oid(1,3,6,1,5,5,7,8,5)->X509TextNameKind.XMPP;x509Oid(1,3,6,1,5,5,7,8,7)->X509TextNameKind.DNSSRV;else->return x509FailureLazy {"GeneralNames: unknown OID ${x509List(n.arcs.asSequence())}"}}
    val next=input.drop(1)
    val split=if(next[0]==Asn1Value.Start(x509Context(0))) next.drop(1).split() else null
    val cs=split?.first?.get(0) as? Asn1Value.StringValue
    if(cs==null || split.first[1]!=null) {
        return x509FailureLazy {"GeneralNames: expecting string for ${if(kind==X509TextNameKind.XMPP) "XMPP" else "DNSSRV"} Addr got: ${split?.let {"Just ${x509List(it.first.sequence())}"} ?: "Nothing"}"}
    }
    val chars=cs.value.decodeLazy() ?: return x509Failure("GeneralNames: invalid string for ${if(kind==X509TextNameKind.XMPP) "XMPP" else "DNSSrv"} Addr")
    return x509Read({X509AltName.Text.deferred(kind) {chars}},split.second)
}
internal fun x509Addr(input: X509Input): Asn1ParseResult<X509Read<X509AltName>> {
    if(input[0]==Asn1Value.Start(x509Context(0))) return x509Container(input,x509Context(0),::composed)
    val n=input[0] ?: return x509Failure("empty")
    if(n is Asn1Value.Other && n.kind==Asn1Class.Context) when(n.tag) {
        1L,2L,6L -> return x509Read({X509AltName.Text.deferred(when(n.tag) {1L->X509TextNameKind.RFC822;2L->X509TextNameKind.DNS;else->X509TextNameKind.URI}) {n.bytes.asSequence().map {lazyOf(it.toInt() and 255)}}},input.drop(1))
        7L -> return x509Read({X509AltName.IP.deferred {n.bytes}},input.drop(1))
        4L -> return when(val raw=x509Binary(n.bytes,Asn1EncodingRules.DER)) {
            is Asn1ParseResult.Failure -> raw
            is Asn1ParseResult.Success -> when(val parsed=x509Run(x509DnFromInput(raw.value))) {is Asn1ParseResult.Failure -> parsed;is Asn1ParseResult.Success -> x509Read({X509AltName.DN(parsed.value)},input.drop(1))}
        }
    }
    return x509FailureLazy {"GeneralNames: not coping with unknown stream $n"}
}
internal fun x509GeneralNames(input: X509Input)=x509Container(input,x509Seq) {x509Many(it,::x509Addr)}
