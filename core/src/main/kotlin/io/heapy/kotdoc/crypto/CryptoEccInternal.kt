// Crypto.Internal.ECC, crypton 2.0.1; Kazu Yamamoto and crypton contributors.
// Full upstream notice: licenses/crypton-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto

import io.heapy.kotdoc.x509.X509CurveName
import io.heapy.kotdoc.x509.X509Ec
import io.heapy.kotdoc.x509.X509EcCurve
import io.heapy.kotdoc.x509.X509EcPoint
import java.math.BigInteger
import java.util.ArrayDeque

private fun eccIntegerArgument(n: BigInteger)=if(n.signum()<0) "($n)" else n.toString()

internal sealed interface EccMulResult {
    data class Point(val x: BigInteger, val y: BigInteger) : EccMulResult {
        override fun toString()="MulPoint ${eccIntegerArgument(x)} ${eccIntegerArgument(y)}"
    }
    data object Infinity : EccMulResult { override fun toString()="MulInfinity" }
    data object Unsupported : EccMulResult { override fun toString()="MulUnsupported" }
}
internal sealed interface EccCurveField {
    data class Prime(val p: BigInteger, val a: BigInteger, val b: BigInteger) : EccCurveField {
        override fun toString()="Prime ${eccIntegerArgument(p)} ${eccIntegerArgument(a)} ${eccIntegerArgument(b)}"
    }
    data class Binary(val fx: BigInteger, val b: BigInteger) : EccCurveField {
        override fun toString()="Binary ${eccIntegerArgument(fx)} ${eccIntegerArgument(b)}"
    }
}

internal object CryptoEccInternal {
    private val zero=BigInteger.ZERO
    private val one=BigInteger.ONE
    class Table(val words: LongArray, val offset: Int, val size: Int)
    sealed interface Event {
        data class Allocate(val size: Int) : Event
        data class Released(val bytes: ByteArray) : Event
        data class TableAllocate(val size: Int) : Event
    }
    /** Only the observer sets poison/status; production allocations use ordinary JVM arrays. */
    class Audit(val poison: Int = 165, val status: Int? = null) {
        val calls=ArrayList<Int>()
        val events=ArrayList<Event>()
        val tables=ArrayList<Table>()
    }
    private inline fun <T> buffer(widths: List<Int>, audit: Audit?, action: (List<CryptoByteSpan>)->T): T {
        val size=widths.sum(); val offset=if(audit==null) 0 else 3
        val bytes=ByteArray(size+if(audit==null) 0 else 7)
        if(audit!=null) {
            bytes.fill(audit.poison.toByte()); byteArrayOf(17,34,51).copyInto(bytes)
            byteArrayOf(68,85,102,119).copyInto(bytes,offset+size)
            audit.events.add(Event.Allocate(size))
        }
        var at=offset
        val spans=widths.map { n->CryptoByteSpan(bytes,at,n).also { at+=n } }
        return try { action(spans) } finally { audit?.events?.add(Event.Released(bytes.copyOf())) }
    }
    private fun write(n: Lazy<BigInteger>, span: CryptoByteSpan) = CryptoSerialize.rawWriteDeferred(n,span.data,span.offset,lazyOf(span.size),true)
    private fun result(code: Int, x: CryptoByteSpan, y: CryptoByteSpan): EccMulResult = when(code) {
        0->EccMulResult.Point(CryptoSerialize.rawOs2ip(x.data,x.offset,x.size),CryptoSerialize.rawOs2ip(y.data,y.offset,y.size))
        1->EccMulResult.Infinity
        else->EccMulResult.Unsupported
    }

    fun primeCurveMul(p: BigInteger,a: BigInteger,b: BigInteger,width: Int,k: BigInteger,x: BigInteger,y: BigInteger) =
        primeCurveMulDeferred(lazyOf(p),lazyOf(a),lazyOf(b),lazyOf(width),lazyOf(k),lazyOf(x),lazyOf(y))
    fun primeCurveMulDeferred(p: Lazy<BigInteger>,a: Lazy<BigInteger>,b: Lazy<BigInteger>,width: Lazy<Int>,k: Lazy<BigInteger>,x: Lazy<BigInteger>,y: Lazy<BigInteger>,audit: Audit? = null): EccMulResult {
        val plen=CryptoNumberBasic.numBytes(p.value)
        if(p.value<=zero || !p.value.testBit(0) || width.value<=0 || k.value<zero) return EccMulResult.Unsupported
        return buffer(List(7) { plen }+width.value,audit) { s ->
            write(x,s[2]); write(y,s[3]); write(a,s[4]); write(b,s[5]); write(p,s[6]); write(k,s[7])
            audit?.calls?.add(1)
            val ret=audit?.status ?: CryptoPrimeEcc.multiply(s[0],s[1],s[2],s[3],s[7],s[4],s[5],s[6])
            write(lazyOf(zero),s[7])
            result(ret,s[0],s[1])
        }
    }
    fun primeCurveTableDeferred(p: Lazy<BigInteger>,a: Lazy<BigInteger>,b: Lazy<BigInteger>,width: Lazy<Int>,x: Lazy<BigInteger>,y: Lazy<BigInteger>,audit: Audit? = null): CryptoMaybe<Table> {
        width.value // The pinned strict size worker enters klen before plen.
        val plen=CryptoNumberBasic.numBytes(p.value)
        val size=CryptoPrimeEcc.tableSize(plen.toUInt(),width.value.toUInt())
        if(p.value<=zero || !p.value.testBit(0) || width.value<=0 || size==0u) return CryptoMaybe.None
        val offset=if(audit==null) 0 else 2
        var fill=0L
        if(audit!=null) for(i in 0..<8) fill=fill or ((audit.poison.toLong() and 255L) shl (8*i))
        val table=Table(LongArray((size.toLong()/8).toInt()+if(audit==null) 0 else 4) { fill },offset,size.toInt())
        audit?.events?.add(Event.TableAllocate(table.size)); audit?.tables?.add(table)
        return buffer(List(5) { plen },audit) { s->
            write(x,s[0]); write(y,s[1]); write(a,s[2]); write(b,s[3]); write(p,s[4])
            audit?.calls?.add(2)
            val ret=audit?.status ?: CryptoPrimeEcc.buildTable(table.words,s[0],s[1],width.value,s[2],s[3],s[4],tableOffset=table.offset)
            if(ret==0) CryptoMaybe.Some(table) else CryptoMaybe.None
        }
    }
    fun primeCurveTableMulDeferred(table: Lazy<Table>,p: Lazy<BigInteger>,a: Lazy<BigInteger>,b: Lazy<BigInteger>,width: Lazy<Int>,k: Lazy<BigInteger>,audit: Audit? = null): EccMulResult {
        val plen=CryptoNumberBasic.numBytes(p.value)
        if(p.value<=zero || !p.value.testBit(0) || width.value<=0 || k.value<zero) return EccMulResult.Unsupported
        return buffer(List(5) { plen }+width.value,audit) { s->
            write(a,s[2]); write(b,s[3]); write(p,s[4]); write(k,s[5])
            val t=table.value; audit?.calls?.add(3)
            val ret=audit?.status ?: CryptoPrimeEcc.multiplyTable(s[0],s[1],t.words,s[5],s[2],s[3],s[4],tableOffset=t.offset)
            write(lazyOf(zero),s[5]); result(ret,s[0],s[1])
        }
    }
    fun primeCurveTableMul(table: Table,p: BigInteger,a: BigInteger,b: BigInteger,width: Int,k: BigInteger) =
        primeCurveTableMulDeferred(lazyOf(table),lazyOf(p),lazyOf(a),lazyOf(b),lazyOf(width),lazyOf(k))

    class BaseTableCache {
        private class Entry(val p: BigInteger,val a: BigInteger,val b: BigInteger,val width: Int,val x: BigInteger,val y: BigInteger) {
            @Volatile private var stored: CryptoMaybe<Table>? = null
            fun get(audit: Audit?): CryptoMaybe<Table> {
                stored?.let { return it }
                return synchronized(this) {
                    stored ?: primeCurveTableDeferred(lazyOf(p),lazyOf(a),lazyOf(b),lazyOf(width),lazyOf(x),lazyOf(y),audit).also { stored=it }
                }
            }
        }
        private val entries=X509CurveName.entries.mapNotNull { name->
            val c=X509Ec.getCurveByName(name)
            if(c !is X509EcCurve.Prime) null else {
                val cc=c.common; val g=cc.generator as X509EcPoint.Affine
                Entry(c.modulus,cc.a,cc.b,CryptoNumberBasic.numBytes(cc.order),g.x,g.y)
            }
        }
        fun lookup(p: Lazy<BigInteger>,a: Lazy<BigInteger>,b: Lazy<BigInteger>,width: Lazy<Int>,x: Lazy<BigInteger>,y: Lazy<BigInteger>,audit: Audit? = null): CryptoMaybe<Table> {
            for(e in entries) if(p.value==e.p && a.value==e.a && b.value==e.b && width.value==e.width && x.value==e.x && y.value==e.y) return e.get(audit)
            return CryptoMaybe.None
        }
        fun warm() { entries.forEach { it.get(null) } }
    }
    private val baseTables by lazy { BaseTableCache() }
    fun baseTable(p: BigInteger,a: BigInteger,b: BigInteger,width: Int,x: BigInteger,y: BigInteger) =
        baseTableDeferred(lazyOf(p),lazyOf(a),lazyOf(b),lazyOf(width),lazyOf(x),lazyOf(y))
    fun baseTableDeferred(p: Lazy<BigInteger>,a: Lazy<BigInteger>,b: Lazy<BigInteger>,width: Lazy<Int>,x: Lazy<BigInteger>,y: Lazy<BigInteger>,audit: Audit? = null,cache: BaseTableCache = baseTables) =
        cache.lookup(p,a,b,width,x,y,audit)

    fun binaryCurveCDeferred(fx: Lazy<BigInteger>,b: Lazy<BigInteger>,width: Lazy<Int>,k: Lazy<BigInteger>,x: Lazy<BigInteger>,y: Lazy<BigInteger>,audit: Audit? = null): EccMulResult {
        val flen=(CryptoNumberBasic.numBits(fx.value)-1+7)/8
        val fxlen=CryptoNumberBasic.numBytes(fx.value)
        if(fx.value<=one || width.value<=0 || k.value<zero || x.value<=zero || flen<=0) return EccMulResult.Unsupported
        return buffer(List(5) { flen }+listOf(fxlen,width.value),audit) { s->
            write(x,s[2]); write(y,s[3]); write(b,s[4]); write(fx,s[5]); write(k,s[6])
            audit?.calls?.add(4)
            val ret=audit?.status ?: CryptoBinaryEcc.multiply(s[0],s[1],s[2],s[3],s[6],s[4],s[5])
            write(lazyOf(zero),s[6]); result(ret,s[0],s[1])
        }
    }
    fun binaryCurveC(fx: BigInteger,b: BigInteger,width: Int,k: BigInteger,x: BigInteger,y: BigInteger) =
        binaryCurveCDeferred(lazyOf(fx),lazyOf(b),lazyOf(width),lazyOf(k),lazyOf(x),lazyOf(y))
    fun curveMul(field: EccCurveField,order: BigInteger,k: BigInteger,x: BigInteger,y: BigInteger,base: Boolean) =
        curveMulDeferred(lazyOf(field),lazyOf(order),lazyOf(k),lazyOf(x),lazyOf(y),lazyOf(base))
    fun curveMulDeferred(field: Lazy<EccCurveField>,order: Lazy<BigInteger>,k: Lazy<BigInteger>,x: Lazy<BigInteger>,y: Lazy<BigInteger>,base: Lazy<Boolean>,audit: Audit? = null,cache: BaseTableCache = baseTables): EccMulResult {
        val width=maxOf(CryptoNumberBasic.numBytes(k.value),CryptoNumberBasic.numBytes(order.value))
        return when(val f=field.value) {
            is EccCurveField.Prime -> {
                val p=lazyOf(f.p); val a=lazyOf(f.a); val b=lazyOf(f.b); val w=lazyOf(width)
                val table=if(base.value && width==CryptoNumberBasic.numBytes(order.value)) cache.lookup(p,a,b,w,x,y,audit) else CryptoMaybe.None
                when(table) {
                    is CryptoMaybe.Some -> primeCurveTableMulDeferred(lazy { table.value },p,a,b,w,k,audit)
                    CryptoMaybe.None -> primeCurveMulDeferred(p,a,b,w,k,x,y,audit)
                }
            }
            is EccCurveField.Binary -> {
                if(x.value==zero) EccMulResult.Unsupported else {
                    val fx=lazyOf(f.fx); val b=lazyOf(f.b)
                    when(val native=binaryCurveCDeferred(fx,b,lazyOf(width),k,x,y,audit)) {
                        EccMulResult.Unsupported -> binaryCurveMulDeferred(fx,b,lazyOf(width*8),k,x,y)
                        else -> native
                    }
                }
            }
        }
    }

    /** A memoized demand graph avoids host-stack growth while retaining source thunk order. */
    private class Num(val op: Int, val args: Array<Num> = emptyArray(), val input: Lazy<BigInteger>? = null) {
        var value: BigInteger? = null
    }
    private class BinaryGraph(fx: Lazy<BigInteger>) {
        val f=Num(0,input=fx)
        fun input(n: Lazy<BigInteger>)=Num(0,input=n)
        fun constant(n: BigInteger)=input(lazyOf(n))
        // These are this module's emitted library-call demands, including left-first
        // integerXor, not the standalone F2m observer's specialized worker entry.
        fun add(a: Num,b: Num)=Num(1,arrayOf(a,b))
        fun mul(a: Num,b: Num)=Num(2,arrayOf(f,a,b))
        fun square(a: Num)=Num(3,arrayOf(f,a))
        fun value(root: Num): BigInteger {
            val stack=ArrayDeque<Num>(); stack.addLast(root)
            while(stack.isNotEmpty()) {
                val n=stack.peekLast()
                if(n.value!=null) { stack.removeLast(); continue }
                if(n.op==3 && n.args[0].value?.signum()==-1) error("modF2m: negative number represent no binary polynomial")
                val missing=n.args.firstOrNull { it.value==null }
                if(missing!=null) { stack.addLast(missing); continue }
                n.value=when(n.op) {
                    0->n.input!!.value
                    1->n.args[0].value!!.xor(n.args[1].value!!)
                    2->F2m.mulF2m(n.args[0].value!!,n.args[1].value!!,n.args[2].value!!)
                    3->F2m.squareF2m(n.args[0].value!!,n.args[1].value!!)
                    else->error("unknown binary field demand")
                }
                stack.removeLast()
            }
            return root.value!!
        }
    }
    private class Projective(val x: Num,val z: Num)
    fun binaryCurveMul(fx: BigInteger,b: BigInteger,bits: Int,k: BigInteger,x: BigInteger,y: BigInteger) =
        binaryCurveMulDeferred(lazyOf(fx),lazyOf(b),lazyOf(bits),lazyOf(k),lazyOf(x),lazyOf(y))
    fun binaryCurveMulDeferred(fx: Lazy<BigInteger>,b: Lazy<BigInteger>,bits: Lazy<Int>,k: Lazy<BigInteger>,x: Lazy<BigInteger>,y: Lazy<BigInteger>): EccMulResult {
        if(bits.value<=0 || k.value<zero || x.value==zero) return EccMulResult.Unsupported
        // Entry into the pinned shared mulF2m worker precedes the projective fields.
        if(fx.value<zero) error("mulF2m: negative number represent no binary polynomial")
        val g=BinaryGraph(fx); val xx=g.input(x); val bb=g.input(b)
        fun add(a: Projective,b: Projective): Projective {
            val t1=g.mul(a.x,b.z); val t2=g.mul(b.x,a.z); val z=g.square(g.add(t1,t2))
            return Projective(g.add(g.mul(xx,z),g.mul(t1,t2)),z)
        }
        fun double(a: Projective): Projective {
            val x2=g.square(a.x); val z2=g.square(a.z)
            return Projective(g.add(g.square(x2),g.mul(bb,g.square(z2))),g.mul(x2,z2))
        }
        var p1=Projective(g.constant(one),g.constant(zero)); var p2=Projective(xx,g.constant(one))
        for(i in bits.value-1 downTo 0) {
            val sum=add(p1,p2)
            if(k.value.testBit(i)) { val d=double(p2); p1=sum; p2=d } else { val d=double(p1); p1=d; p2=sum }
        }
        val z1=g.value(p1.z)
        if(z1==zero) return EccMulResult.Infinity
        val z2=g.value(p2.z)
        fun addLeft(a: Lazy<BigInteger>,b: Lazy<BigInteger>): BigInteger {
            val av=a.value; return av.xor(b.value)
        }
        fun multiply(a: Lazy<BigInteger>,b: Lazy<BigInteger>): BigInteger {
            val f=fx.value
            if(f<zero) error("mulF2m: negative number represent no binary polynomial")
            val av=a.value
            if(av<zero) error("mulF2m: negative number represent no binary polynomial")
            val bv=b.value
            if(bv<zero) error("mulF2m: negative number represent no binary polynomial")
            return F2m.mulF2m(f,av,bv)
        }
        if(z2==zero) return EccMulResult.Point(x.value,addLeft(x,y))
        val xa=F2m.divF2mDeferred(fx,lazy { g.value(p1.x) },lazyOf(z1))
        if(xa !is F2mMaybe.Some) return EccMulResult.Unsupported
        val xb=F2m.divF2mDeferred(fx,lazy { g.value(p2.x) },lazyOf(z2))
        if(xb !is F2mMaybe.Some) return EccMulResult.Unsupported
        val u=lazy { addLeft(lazy { xa.value },x) }
        val v=lazy { addLeft(lazy { xb.value },x) }
        val inner=lazy { addLeft(lazy { addLeft(lazy { multiply(u,v) },lazy { F2m.squareF2mDeferred(fx,x) }) },y) }
        return when(val w=F2m.divF2mDeferred(fx,lazy { multiply(u,inner) },x)) {
            F2mMaybe.None -> EccMulResult.Unsupported
            is F2mMaybe.Some -> {
                val first=xa.value
                EccMulResult.Point(first,addLeft(lazy { w.value },y))
            }
        }
    }
}
