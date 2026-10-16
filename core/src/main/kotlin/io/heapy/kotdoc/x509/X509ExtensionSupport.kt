// Data.X509.Ext / Data.ASN1.Parse demand, crypton-x509 1.9.2.
// Vincent Hanquez; licenses/crypton-x509-BSD-3-Clause.txt.
package io.heapy.kotdoc.x509

import io.heapy.kotdoc.asn1.*
import io.heapy.kotdoc.layout.CharacterStringChars

/** Immutable views over memoized values; looking at a prefix never requests its tail. */
internal class X509Input(private val get: (Int)->Asn1Value?) {
    operator fun get(index: Int)=get.invoke(index)
    fun drop(count: Int)=X509Input {get(it+count)}
    fun sequence(): Sequence<Asn1Value> = kotlin.sequences.sequence {var i=0;while(true) yield(get(i++) ?: break)}
    fun view(): List<Asn1Value> = object: AbstractList<Asn1Value>() {
        override val size get()=all().size
        override fun isEmpty()=this@X509Input[0]==null
        override fun get(index: Int): Asn1Value {if(index<0) throw IndexOutOfBoundsException("index: $index");return this@X509Input[index] ?: throw IndexOutOfBoundsException("index: $index")}
        override fun iterator()=this@X509Input.sequence().iterator()
    }
    fun all(): List<Asn1Value> = buildList {var i=0;while(true) add(this@X509Input[i++] ?: break)}
    fun split(): Pair<X509Input,X509Input> {
        val cache=mutableListOf<Asn1Value>();var index=0;var depth=0L;var finished=false
        fun next() {
            val v=get(index)
            if(v==null) {finished=true;return}
            index++
            if(v is Asn1Value.End && depth==0L) {finished=true;return}
            cache.add(v);if(v is Asn1Value.Start) depth++ else if(v is Asn1Value.End) depth--
        }
        val body=X509Input {at -> while(cache.size<=at && !finished) next();cache.getOrNull(at)}
        val rest=X509Input {at -> while(!finished) next();get(index+at)}
        return body to rest
    }
    companion object {
        fun from(values: Sequence<Asn1Value>): X509Input {
            val iterator by lazy {values.iterator()};val cache=mutableListOf<Asn1Value>();var done=false
            return X509Input {at -> while(cache.size<=at && !done) {if(iterator.hasNext()) cache.add(iterator.next()) else done=true};cache.getOrNull(at)}
        }
    }
}
internal data class X509Read<A>(val value: Lazy<A>,val rest: X509Input)
internal fun <A> x509Failure(message: String): Asn1ParseResult<A> = Asn1ParseResult.Failure(message)
internal fun <A> x509FailureLazy(message: ()->String): Asn1ParseResult<A> = Asn1ParseResult.Failure.deferred {CharacterStringChars.fromString(message())}
internal fun <A> x509Read(value: ()->A,rest: X509Input)=Asn1ParseResult.Success(X509Read(lazy(value),rest))
internal fun <A> x509Many(input: X509Input,parse: (X509Input)->Asn1ParseResult<X509Read<A>>): Asn1ParseResult<X509Read<List<Lazy<A>>>> {
    val out=mutableListOf<Lazy<A>>();var rest=input
    while(rest[0]!=null) when(val result=parse(rest)) {
        is Asn1ParseResult.Failure -> return result
        is Asn1ParseResult.Success -> {out.add(result.value.value);rest=result.value.rest}
    }
    return x509Read({out.toList()},rest)
}
internal fun <A> x509Container(input: X509Input,kind: Asn1ConstructionType,parse: (X509Input)->Asn1ParseResult<X509Read<A>>): Asn1ParseResult<X509Read<A>> {
    val first=input[0] ?: return x509Failure("empty")
    if(first!=Asn1Value.Start(kind)) return x509Failure("not an expected container")
    val (inside,rest)=input.drop(1).split()
    return when(val result=parse(inside)) {
        is Asn1ParseResult.Failure -> result
        is Asn1ParseResult.Success -> if(result.value.rest[0]!=null) x509FailureLazy {"runParseASN1: remaining state ${x509List(result.value.rest.sequence())}"} else Asn1ParseResult.Success(X509Read(result.value.value,rest))
    }
}
internal fun <A> x509Run(result: Asn1ParseResult<X509Read<A>>): Asn1ParseResult<A> = when(result) {
    is Asn1ParseResult.Failure -> result
    is Asn1ParseResult.Success -> if(result.value.rest[0]!=null) x509FailureLazy {"runParseASN1: remaining state ${x509List(result.value.rest.sequence())}"} else Asn1ParseResult.Success.deferred {result.value.value.value}
}
internal fun x509Binary(bytes: ByteArray,rules: Asn1EncodingRules=Asn1EncodingRules.BER): Asn1ParseResult<X509Input> = when(val r=Asn1BinaryEncoding().decode(bytes,rules)) {
    is Asn1Result.Failure -> x509FailureLazy {r.error.show()}
    is Asn1Result.Success -> Asn1ParseResult.Success(X509Input.from(r.value))
}
abstract class X509ExtensionCodec<A: X509TypedExtension>(val kind: X509ExtensionKind,oid: Asn1Oid,val hasNestedAsn1: Boolean=true) {
    private val storedOid=oid.toList();val oid get()=storedOid.toList()
    abstract fun encodeStream(value: A): Sequence<Asn1Value>
    fun encode(value: A)=encodeStream(value).toList()
    internal abstract fun decodeInput(input: X509Input): Asn1ParseResult<A>
    fun decode(values: List<Asn1Value>)=decodeSequence(values.toList().asSequence())
    fun decodeSequence(values: Sequence<Asn1Value>)=decodeInput(X509Input.from(values))
    open fun encodeBytes(value: A)=Asn1BinaryEncoding().encodeDer(encode(value))
    fun decodeBytes(bytes: ByteArray): Asn1ParseResult<A> {val owned=bytes.copyOf();return decodeBytesLazy {owned}}
    internal open fun decodeBytesLazy(bytes: ()->ByteArray): Asn1ParseResult<A> = when(val r=x509Binary(bytes())) {is Asn1ParseResult.Failure -> r;is Asn1ParseResult.Success -> decodeInput(r.value)}
    fun encodeRaw(critical: Boolean,value: A)=X509ExtensionRaw.deferred({oid},{critical},{encodeBytes(value)})
    fun decodeRaw(raw: X509ExtensionRaw): Asn1Maybe<Asn1ParseResult<A>> {
        if(raw.oid!=oid) return Asn1Maybe.None
        return Asn1Maybe.Some.deferred {
            if(!hasNestedAsn1) decodeBytesLazy {raw.content} else when(val r=raw.tryAsn1()) {is Asn1ParseResult.Failure -> r;is Asn1ParseResult.Success -> decodeSequence(r.value)}
        }
    }
    fun getE(extensions: X509Extensions): Asn1Maybe<Asn1ParseResult<A>> {
        for(raw in extensions.values ?: return Asn1Maybe.None) {val result=decodeRaw(raw);if(result is Asn1Maybe.Some) return result}
        return Asn1Maybe.None
    }
    fun get(extensions: X509Extensions): Asn1Maybe<A> {
        for(raw in extensions.values ?: return Asn1Maybe.None) when(val result=decodeRaw(raw)) {
            Asn1Maybe.None -> Unit
            is Asn1Maybe.Some -> when(val decoded=result.value) {is Asn1ParseResult.Failure -> Unit;is Asn1ParseResult.Success -> return Asn1Maybe.Some.deferred {decoded.value}}
        }
        return Asn1Maybe.None
    }
}
