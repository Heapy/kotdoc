// crypton 2.0.1 cbits/crypton_f2m.c; package notice: licenses/crypton-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto

/** The source's software carry-less backend and fixed-width binary-curve ladder. */
internal object CryptoBinaryEcc {
    class Audit(val failAllocation: Int = 0) {
        val operations = LongArray(10)
        val ladderOperations = LongArray(10)
        var swapAddressHash = 0L
        var swapReads = 0L
        val allocations = ArrayList<Long>()
        val frees = ArrayList<Int>()
        val cleared = ArrayList<Boolean>()
        fun count(i: Int) { operations[i]++ }
    }
    class Region(val data: LongArray, val offset: Int = 0) {
        operator fun get(i: Int) = data[offset+i]
        operator fun set(i: Int, v: Long) { data[offset+i] = v }
        fun at(i: Int) = Region(data,offset+i)
        fun zero(n: Int) = data.fill(0,offset,offset+n)
        fun copyFrom(a: Region, n: Int) = a.data.copyInto(data,offset,a.offset,a.offset+n)
    }
    fun clmul32(x: Long, y: Long): Long {
        val x0=x and 0x11111111L; val x1=x and 0x22222222L
        val x2=x and 0x44444444L; val x3=x and 0x88888888L
        val y0=y and 0x11111111L; val y1=y and 0x22222222L
        val y2=y and 0x44444444L; val y3=y and 0x88888888L
        val z0=(x0*y0) xor (x1*y3) xor (x2*y2) xor (x3*y1)
        val z1=(x0*y1) xor (x1*y0) xor (x2*y3) xor (x3*y2)
        val z2=(x0*y2) xor (x1*y1) xor (x2*y0) xor (x3*y3)
        val z3=(x0*y3) xor (x1*y2) xor (x2*y1) xor (x3*y0)
        return (z0 and 0x1111111111111111L) or (z1 and 0x2222222222222222L) or
            (z2 and 0x4444444444444444L) or (z3 and 0x8888888888888888uL.toLong())
    }
    fun clmul(a: Long, b: Long, out: Region) {
        val ah=a ushr 32; val bh=b ushr 32
        val t0=clmul32(a,b); val t1=clmul32(ah,bh)
        val t2=clmul32(a xor ah,b xor bh) xor t0 xor t1
        out[0]=t0 xor (t2 shl 32); out[1]=t1 xor (t2 ushr 32)
    }
    fun polyMul(t: Region, a: Region, b: Region, n: Int) {
        t.zero(2*n)
        for (i in 0..<n) for (j in 0..<n) {
            val av=a[i]; val bv=b[j]; val ah=av ushr 32; val bh=bv ushr 32
            val t0=clmul32(av,bv); val t1=clmul32(ah,bh)
            val t2=clmul32(av xor ah,bv xor bh) xor t0 xor t1
            t[i+j]=t[i+j] xor t0 xor (t2 shl 32)
            t[i+j+1]=t[i+j+1] xor t1 xor (t2 ushr 32)
        }
    }
    fun spread(input: Long): Long {
        var x=input
        x=(x or (x shl 16)) and 0x0000ffff0000ffffL
        x=(x or (x shl 8)) and 0x00ff00ff00ff00ffL
        x=(x or (x shl 4)) and 0x0f0f0f0f0f0f0f0fL
        x=(x or (x shl 2)) and 0x3333333333333333L
        return (x or (x shl 1)) and 0x5555555555555555L
    }
    fun polySquare(t: Region, a: Region, n: Int) {
        for (i in 0..<n) { t[2*i]=spread(a[i] and 0xffffffffL); t[2*i+1]=spread(a[i] ushr 32) }
    }
    fun reduce(r: Region, t: Region, n: Int, m: Int, terms: IntArray) {
        val mw=m/64; val mb=m%64
        for (i in 2*n downTo mw+2) {
            val w=t[i-1]; t[i-1]=0
            for (term in terms) {
                val pos=(i-1)*64-m+term; val pw=pos/64; val pb=pos%64
                t[pw]=t[pw] xor (w shl pb)
                if (pb!=0) t[pw+1]=t[pw+1] xor (w ushr (64-pb))
            }
        }
        repeat(2) {
            if (mb==0) return@repeat
            val w=t[mw] ushr mb; t[mw]=t[mw] and ((1L shl mb)-1)
            for (term in terms) {
                val pw=term/64; val pb=term%64
                t[pw]=t[pw] xor (w shl pb)
                if (pb!=0 && pw+1<=mw) t[pw+1]=t[pw+1] xor (w ushr (64-pb))
            }
        }
        r.copyFrom(t,n)
    }
    class Field(val n: Int, val m: Int, val terms: IntArray, val product: Region, val audit: Audit? = null) {
        fun mul(r: Region, a: Region, b: Region) {
            audit?.count(0); audit?.count(7); polyMul(product,a,b,n)
            audit?.count(6); reduce(r,product,n,m,terms)
        }
        fun square(r: Region, a: Region) {
            audit?.count(1); audit?.count(8); polySquare(product,a,n)
            audit?.count(6); reduce(r,product,n,m,terms)
        }
        fun add(r: Region, a: Region, b: Region) { audit?.count(2); for (i in 0..<n) r[i]=a[i] xor b[i] }
        fun isZero(a: Region): Boolean {
            audit?.count(3); var acc=0L
            for (i in 0..<n) acc=acc or a[i]
            return acc==0L
        }
        fun inverse(r: Region, a: Region, tmp: Region) {
            audit?.count(4); tmp.copyFrom(a,n)
            for (i in 1..<m-1) { square(tmp,tmp); mul(tmp,tmp,a) }
            square(r,tmp)
        }
    }
    fun swap(a: Region, b: Region, bit: Long, n: Int, audit: Audit? = null, root: Int = 0) {
        audit?.count(5); val mask=0L-bit
        for (i in 0..<n) {
            audit?.let {
                it.swapReads+=2
                it.swapAddressHash=(it.swapAddressHash*0x100000001b3L) xor (a.offset-root+i).toLong()
                it.swapAddressHash=(it.swapAddressHash*0x100000001b3L) xor (b.offset-root+i).toLong()
            }
            val t=(a[i] xor b[i]) and mask; a[i]=a[i] xor t; b[i]=b[i] xor t
        }
    }

    /** As in C, the caller supplies a valid point with nonzero x over a binary field. */
    fun multiply(outx: CryptoByteSpan, outy: CryptoByteSpan, px: CryptoByteSpan, py: CryptoByteSpan, k: CryptoByteSpan, b: CryptoByteSpan, fx: CryptoByteSpan, audit: Audit? = null): Int {
        val flen=b.size; val fn=(fx.size+7)/8
        if (flen==0 || fx.size==0 || k.size==0 || fn==0) return -1
        fun allocate(size: Int): LongArray? {
            audit?.allocations?.add(size.toLong()*8)
            return try {
                if (audit!=null && audit.allocations.size==audit.failAllocation) throw OutOfMemoryError("controlled allocation failure")
                LongArray(size)
            } catch (_: OutOfMemoryError) { null }
        }
        fun freed(id: Int, data: LongArray) { audit?.frees?.add(id); audit?.cleared?.add(data.all { it==0L }) }
        val tmp=allocate(fn) ?: return -1
        if (CryptoMontgomery.fromBigEndian(tmp,0,fn,fx)!=0) { freed(1,tmp); return -1 }
        var m=0
        for (i in fn downTo 1) {
            if (m==0 && tmp[i-1]!=0L) m=(i-1)*64+64-tmp[i-1].countLeadingZeroBits()-1
        }
        val terms=IntArray(8); var nterms=0
        for (i in 0..<m) if ((tmp[i/64] ushr (i%64)) and 1L != 0L) {
            if (nterms>=8) { freed(1,tmp); return -1 }
            terms[nterms++]=i
        }
        // The polynomial is public, and its temporary is freed without wiping upstream.
        freed(1,tmp)
        if (m==0 || nterms==0) return -1
        val n=(m+64)/64; val data=allocate(14*n) ?: return -1
        val poly=Region(data); val x=poly.at(n); val y=x.at(n); val bb=y.at(n)
        val x1=bb.at(n); val z1=x1.at(n); val x2=z1.at(n); val z2=x2.at(n)
        val t1=z2.at(n); val t2=t1.at(n); val t3=t2.at(n)
        val f=Field(n,m,terms.copyOf(nterms),t3.at(n),audit)
        try {
            if (CryptoMontgomery.fromBigEndian(data,x.offset,n,px)!=0 || CryptoMontgomery.fromBigEndian(data,y.offset,n,py)!=0 || CryptoMontgomery.fromBigEndian(data,bb.offset,n,b)!=0) return -1
            if (f.isZero(x)) return -1
            x1.zero(n); x1[0]=1; z1.zero(n); x2.copyFrom(x,n); z2.zero(n); z2[0]=1
            for (i in k.size*8 downTo 1) {
                audit?.count(9)
                val bit=i-1; val sel=(k[k.size-1-bit/8].toLong() ushr (bit%8)) and 1L
                swap(x1,x2,sel,n,audit); swap(z1,z2,sel,n,audit)
                f.mul(t1,x1,z2); f.mul(t2,x2,z1); f.add(t3,t1,t2); f.square(t3,t3)
                f.mul(t1,t1,t2); f.mul(t2,x,t3); f.add(t2,t2,t1)
                f.square(x1,x1); f.square(z1,z1); f.mul(t1,x1,z1)
                f.square(x1,x1); f.square(z1,z1); f.mul(z1,z1,bb); f.add(x1,x1,z1); z1.copyFrom(t1,n)
                x2.copyFrom(t2,n); z2.copyFrom(t3,n)
                swap(x1,x2,sel,n,audit); swap(z1,z2,sel,n,audit)
            }
            audit?.let { it.operations.copyInto(it.ladderOperations) }
            if (f.isZero(z1)) return 1
            if (f.isZero(z2)) {
                CryptoMontgomery.toBigEndian(outx,data,x.offset,n); f.add(t1,x,y)
                CryptoMontgomery.toBigEndian(outy,data,t1.offset,n); return 0
            }
            f.mul(t1,z1,z2); f.mul(t1,t1,x); f.inverse(t2,t1,t3)
            val xa=x1; val xb=x2; val u=t1; val v=t3
            f.mul(u,z2,x); f.mul(u,u,t2); f.mul(xa,x1,u)
            f.mul(v,z1,x); f.mul(v,v,t2); f.mul(xb,x2,v)
            f.mul(u,z1,z2); f.mul(u,u,t2)
            f.add(v,xa,x); f.add(xb,xb,x); f.mul(xb,v,xb); f.square(t2,x)
            f.add(xb,xb,t2); f.add(xb,xb,y); f.mul(xb,v,xb); f.mul(xb,xb,u); f.add(xb,xb,y)
            CryptoMontgomery.toBigEndian(outx,data,xa.offset,n); CryptoMontgomery.toBigEndian(outy,data,xb.offset,n)
            return 0
        } finally { data.fill(0); freed(2,data) }
    }
}
