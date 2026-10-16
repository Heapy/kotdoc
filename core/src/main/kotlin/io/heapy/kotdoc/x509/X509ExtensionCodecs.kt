// Data.X509.Ext, crypton-x509 1.9.2; Vincent Hanquez.
// licenses/crypton-x509-BSD-3-Clause.txt.
package io.heapy.kotdoc.x509

import io.heapy.kotdoc.asn1.*
import java.math.BigInteger

object X509BasicConstraintsCodec: X509ExtensionCodec<X509BasicConstraints>(X509ExtensionKind.BasicConstraints,x509Oid(2,5,29,19)) {
    override fun encodeStream(value: X509BasicConstraints)=sequence<Asn1Value> {yield(Asn1Value.Start(x509Seq));yield(Asn1Value.BooleanValue(value.authority));value.pathLength?.let {yield(Asn1Value.IntVal(it))};yield(Asn1Value.End(x509Seq))}
    override fun decodeInput(input: X509Input): Asn1ParseResult<X509BasicConstraints> {
        if(input[0]==Asn1Value.Start(x509Seq)) {
            val b=input[1] as? Asn1Value.BooleanValue
            if(b!=null) {
                val n=input[2] as? Asn1Value.IntVal
                if(n!=null && input[3]==Asn1Value.End(x509Seq) && input[4]==null) return if(n.value.signum()<0) x509Failure("invalid pathlen") else Asn1ParseResult.Success.deferred {X509BasicConstraints(b.value,n.value)}
                if(input[2]==Asn1Value.End(x509Seq) && input[3]==null) return Asn1ParseResult.Success.deferred {X509BasicConstraints(b.value,null)}
            }
            if(input[1]==Asn1Value.End(x509Seq) && input[2]==null) return Asn1ParseResult.Success(X509BasicConstraints(false,null))
        }
        return x509Failure("unknown sequence")
    }
}
object X509KeyUsageCodec: X509ExtensionCodec<X509KeyUsage>(X509ExtensionKind.KeyUsage,x509Oid(2,5,29,15)) {
    override fun encodeStream(value: X509KeyUsage)=sequence<Asn1Value> {yield(Asn1Value.BitString.deferred {
        var bits=Asn1BitArray.fromBytes(byteArrayOf(0,0),7)
        for(flag in value.flagCells()) bits=bits.setBit(flag.value.ordinal.toULong())
        bits
    })}
    override fun decodeInput(input: X509Input): Asn1ParseResult<X509KeyUsage> {
        val b=input[0] as? Asn1Value.BitString ?: return x509Failure("unknown sequence")
        if(input[1]!=null) return x509Failure("unknown sequence")
        return Asn1ParseResult.Success.deferred {X509KeyUsage.deferred {sequence {
            val bits=b.value;val end=bits.length-1uL;var i=0uL
            while(true) {val at=i;if(bits.getBit(at)) yield(lazy {X509KeyUsageFlag.fromOrdinal(at.toLong())});if(i==end) break;i++}
        }}}
    }
}
internal val x509Purposes=listOf(X509KeyUsagePurpose.ServerAuth,X509KeyUsagePurpose.ClientAuth,X509KeyUsagePurpose.CodeSigning,X509KeyUsagePurpose.EmailProtection,X509KeyUsagePurpose.TimeStamping,X509KeyUsagePurpose.OCSPSigning)
internal fun x509PurposeOid(p: X509KeyUsagePurpose)=if(p is X509KeyUsagePurpose.Unknown) p.oid else x509Oid(1,3,6,1,5,5,7,3,listOf(1L,2L,3L,4L,8L,9L)[x509Purposes.indexOf(p)])
internal fun x509Purpose(oid: Asn1Oid): X509KeyUsagePurpose=x509Purposes.firstOrNull {x509PurposeOid(it)==oid} ?: X509KeyUsagePurpose.Unknown(oid)
object X509ExtendedKeyUsageCodec: X509ExtensionCodec<X509ExtendedKeyUsage>(X509ExtensionKind.ExtendedKeyUsage,x509Oid(2,5,29,37)) {
    override fun encodeStream(value: X509ExtendedKeyUsage)=sequence<Asn1Value> {yield(Asn1Value.Start(x509Seq));for(p in value.purposeCells()) yield(Asn1Value.Oid.deferred {x509PurposeOid(p.value)});yield(Asn1Value.End(x509Seq))}
    override fun decodeInput(input: X509Input): Asn1ParseResult<X509ExtendedKeyUsage> {
        val parsed=x509Run(x509Container(input,x509Seq) {body -> x509Many(body) {s ->
            val n=s[0] ?: return@x509Many x509Failure("empty")
            if(n !is Asn1Value.Oid) error("invalid content in extended key usage")
            x509Read({x509Purpose(n.arcs)},s.drop(1))
        }})
        return when(parsed) {is Asn1ParseResult.Failure -> parsed;is Asn1ParseResult.Success -> Asn1ParseResult.Success.deferred {X509ExtendedKeyUsage.deferred {parsed.value.asSequence()}}}
    }
}
object X509SubjectKeyIdCodec: X509ExtensionCodec<X509SubjectKeyId>(X509ExtensionKind.SubjectKeyId,x509Oid(2,5,29,14)) {
    override fun encodeStream(value: X509SubjectKeyId)=sequenceOf(Asn1Value.OctetString.deferred {value.bytes})
    override fun decodeInput(input: X509Input): Asn1ParseResult<X509SubjectKeyId> {val b=input[0] as? Asn1Value.OctetString ?: return x509Failure("unknown sequence");return if(input[1]==null) Asn1ParseResult.Success.deferred {X509SubjectKeyId.deferred {b.bytes}} else x509Failure("unknown sequence")}
}
object X509AuthorityKeyIdCodec: X509ExtensionCodec<X509AuthorityKeyId>(X509ExtensionKind.AuthorityKeyId,x509Oid(2,5,29,35)) {
    override fun encodeStream(value: X509AuthorityKeyId)=sequence<Asn1Value> {yield(Asn1Value.Start(x509Seq));yield(Asn1Value.Other.deferred(Asn1Class.Context,0) {value.bytes});yield(Asn1Value.End(x509Seq))}
    override fun decodeInput(input: X509Input): Asn1ParseResult<X509AuthorityKeyId> {
        if(input[0]==Asn1Value.Start(x509Seq)) {val n=input[1] as? Asn1Value.Other;if(n!=null && n.kind==Asn1Class.Context && n.tag==0L && input[2]==Asn1Value.End(x509Seq) && input[3]==null) return Asn1ParseResult.Success.deferred {X509AuthorityKeyId.deferred {n.bytes}}}
        return x509Failure("unknown sequence")
    }
}
object X509SubjectAltNameCodec: X509ExtensionCodec<X509SubjectAltName>(X509ExtensionKind.SubjectAltName,x509Oid(2,5,29,17)) {
    override fun encodeStream(value: X509SubjectAltName)=sequence<Asn1Value> {yield(Asn1Value.Start(x509Seq));for(n in value.cells()) yieldAll(x509EncodeAlt(n.value));yield(Asn1Value.End(x509Seq))}
    override fun decodeInput(input: X509Input): Asn1ParseResult<X509SubjectAltName> = when(val result=x509Run(x509GeneralNames(input))) {
        is Asn1ParseResult.Failure -> result
        is Asn1ParseResult.Success -> Asn1ParseResult.Success.deferred {X509SubjectAltName.deferred {result.value.asSequence()}}
    }
}
object X509NetscapeCommentCodec: X509ExtensionCodec<X509NetscapeComment>(X509ExtensionKind.NetscapeComment,x509Oid(2,16,840,1,113730,1,13),false) {
    override fun encodeStream(value: X509NetscapeComment)=sequence<Asn1Value> {error("Extension: Netscape Comment do not contain nested ASN1")}
    override fun decodeInput(input: X509Input): Asn1ParseResult<X509NetscapeComment> = error("Extension: Netscape Comment do not contain nested ASN1")
    override fun encodeBytes(value: X509NetscapeComment)=value.bytes
    override fun decodeBytesLazy(bytes: () -> ByteArray)=Asn1ParseResult.Success.deferred {X509NetscapeComment.deferred(bytes)}
}
object X509CrlDistributionPointsCodec: X509ExtensionCodec<X509CrlDistributionPoints>(X509ExtensionKind.CrlDistributionPoints,x509Oid(2,5,29,31)) {
    override fun encodeStream(value: X509CrlDistributionPoints)=sequence<Asn1Value> {error("extEncode ExtCrlDistributionPoints unimplemented")}
    override fun decodeInput(input: X509Input): Asn1ParseResult<X509CrlDistributionPoints> = error("extDecode ExtCrlDistributionPoints unimplemented")
}
object X509NameConstraintsCodec: X509ExtensionCodec<X509NameConstraints>(X509ExtensionKind.NameConstraints,x509Oid(2,5,29,30)) {
    private fun subtree(value: X509GeneralSubtree)=sequence<Asn1Value> {
        yield(Asn1Value.Start(x509Seq));yieldAll(x509EncodeAlt(value.base))
        if(value.minimum!=BigInteger.ZERO) yield(Asn1Value.Other.deferred(Asn1Class.Context,0) {Asn1BinaryEncoding().encodeDer(listOf(Asn1Value.IntVal(value.minimum)))})
        value.maximum?.let {m -> yield(Asn1Value.Other.deferred(Asn1Class.Context,1) {Asn1BinaryEncoding().encodeDer(listOf(Asn1Value.IntVal(m)))})}
        yield(Asn1Value.End(x509Seq))
    }
    override fun encodeStream(value: X509NameConstraints)=sequence<Asn1Value> {
        yield(Asn1Value.Start(x509Seq))
        for((tag,values) in listOf(0L to value.permitted,1L to value.excluded)) if(values.isNotEmpty()) {
            yield(Asn1Value.Start(x509Context(tag)));yield(Asn1Value.Start(x509Seq));for(s in values) yieldAll(subtree(s));yield(Asn1Value.End(x509Seq));yield(Asn1Value.End(x509Context(tag)))
        }
        yield(Asn1Value.End(x509Seq))
    }
    private fun distance(input: X509Input,tag: Long): Pair<BigInteger?,X509Input> {
        val n=input[0] as? Asn1Value.Other ?: return null to input
        if(n.kind!=Asn1Class.Context || n.tag!=tag) return null to input
        val r=x509Binary(n.bytes,Asn1EncodingRules.DER)
        if(r is Asn1ParseResult.Success) {val i=r.value[0] as? Asn1Value.IntVal;if(i!=null && r.value[1]==null) return i.value to input.drop(1)}
        return null to input
    }
    private fun decodeSubtree(input: X509Input): Asn1ParseResult<X509Read<X509GeneralSubtree>> {
        if(input[0]!=Asn1Value.Start(x509Seq)) return x509Failure("GeneralSubtree: invalid ASN1")
        return when(val r=x509Addr(input.drop(1))) {
            is Asn1ParseResult.Failure -> r
            is Asn1ParseResult.Success -> {
                val (minimum,rest2)=distance(r.value.rest,0);val (maximum,rest3)=distance(rest2,1)
                if(rest3[0]!=Asn1Value.End(x509Seq)) x509Failure("GeneralSubtree: missing end sequence") else x509Read({X509GeneralSubtree(r.value.value.value,minimum ?: BigInteger.ZERO,maximum)},rest3.drop(1))
            }
        }
    }
    private fun subtrees(input: X509Input): Asn1ParseResult<X509Read<List<Lazy<X509GeneralSubtree>>>> {
        val values=mutableListOf<Lazy<X509GeneralSubtree>>();var rest=input
        while(true) {
            val n=rest[0]
            if(n is Asn1Value.End && n.kind is Asn1ConstructionType.Container && n.kind.kind==Asn1Class.Context) return x509Read({values.toList()},rest.drop(1))
            when(val r=decodeSubtree(rest)) {is Asn1ParseResult.Failure -> return r;is Asn1ParseResult.Success -> {values.add(r.value.value);rest=r.value.rest}}
        }
    }
    override fun decodeInput(input: X509Input): Asn1ParseResult<X509NameConstraints> {
        if(input[0]!=Asn1Value.Start(x509Seq)) return x509Failure("NameConstraints: expected SEQUENCE")
        var rest=input.drop(1);var permitted=emptyList<Lazy<X509GeneralSubtree>>();var excluded=emptyList<Lazy<X509GeneralSubtree>>()
        while(true) {
            if(rest[0]==Asn1Value.End(x509Seq)) {val p=permitted;val e=excluded;return Asn1ParseResult.Success.deferred {X509NameConstraints(p.map {it.value},e.map {it.value})}}
            val n=rest[0]
            if(n !is Asn1Value.Start || n.kind !is Asn1ConstructionType.Container || n.kind.kind!=Asn1Class.Context || n.kind.tag !in 0..1) return x509Failure("Invalid NameConstraints")
            when(val r=subtrees(rest.drop(1))) {is Asn1ParseResult.Failure -> return r;is Asn1ParseResult.Success -> {if(n.kind.tag==0L) permitted=r.value.value.value else excluded=r.value.value.value;rest=r.value.rest}}
        }
    }
}
object X509ExtensionCodecs {
    val all: List<X509ExtensionCodec<out X509TypedExtension>> = listOf(X509BasicConstraintsCodec,X509KeyUsageCodec,X509ExtendedKeyUsageCodec,X509SubjectKeyIdCodec,X509SubjectAltNameCodec,X509AuthorityKeyIdCodec,X509CrlDistributionPointsCodec,X509NameConstraintsCodec,X509NetscapeCommentCodec)
    val recognizedOids get()=all.map {it.oid}
    @Suppress("UNCHECKED_CAST") fun forKind(kind: X509ExtensionKind)=all[kind.ordinal] as X509ExtensionCodec<X509TypedExtension>
}
