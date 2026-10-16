// Copyright 2013 The Android Open Source Project; crypton 2.0.1 p256/p256.c.
// Complete upstream notice: licenses/crypton-p256-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto

/** The source's 32/64-bit digit algorithms; neither profile uses BigInteger. */
internal class CryptoP256Words(val bits: Int) {
    val digits=256/bits
    private val mask=if(bits==32) 0xffffffffL else -1L
    class Region(val data: LongArray,val offset: Int=0) {
        operator fun get(i: Int)=data[offset+i]
        operator fun set(i: Int,v: Long) { data[offset+i]=v }
        fun at(i: Int)=Region(data,offset+i)
        fun copy(n: Int)=Region(data.copyOfRange(offset,offset+n))
        fun copyFrom(a: Region,n: Int)=a.data.copyInto(data,offset,a.offset,a.offset+n)
        fun zero(n: Int)=data.fill(0,offset,offset+n)
    }
    class Audit(val inverseLimit: Int=0) {
        val ops=LongArray(28)
        var quotientCorrections=0L
        var stopped=false
        var inverseState=LongArray(0)
        fun count(i: Int) { ops[i]++ }
    }
    class ObservationStopped : RuntimeException()
    private fun constant(a: LongArray)=Region(if(bits==64) a else a.flatMap { listOf(it and 0xffffffffL,it ushr 32) }.toLongArray())
    val n=constant(longArrayOf(0xf3b9cac2fc632551uL.toLong(),0xbce6faada7179e84uL.toLong(),-1,0xffffffff00000000uL.toLong()))
    val p=constant(longArrayOf(-1,0x00000000ffffffff,0,0xffffffff00000001uL.toLong()))
    val b=constant(longArrayOf(0x3bce3c3e27d2604b,0x651d06b0cc53b0f6,0xb3ebbd55769886bcuL.toLong(),0x5ac635d8aa3a93e7))
    val r2=constant(longArrayOf(0x83244c95be79eea2uL.toLong(),0x4699799c49bd6fa6,0x2845b2392b6bec59,0x66e12d94f3d95620))
    val one=constant(longArrayOf(1,0,0,0))
    private val factor=if(bits==32) 0xee00bc4fL else 0xccd1c8aaee00bc4fuL.toLong()
    init { require(bits==32 || bits==64) }
    private fun fresh()=Region(LongArray(digits))
    private fun carry(a: Long,b: Long,sum: Long)=((a and b) or ((a or b) and sum.inv())) ushr 63
    private fun nonzero(a: Long)=(a or -a) ushr 63
    fun init(a: Region,audit: Audit?=null) { audit?.count(4);a.zero(digits) }
    fun clear(a: Region,audit: Audit?=null) { audit?.count(5);init(a,audit) }
    fun bit(a: Region,index: Int,audit: Audit?=null): Int { audit?.count(6);return ((a[index/bits] ushr (index and (bits-1))) and 1L).toInt() }
    fun zero(a: Region,audit: Audit?=null): Boolean { audit?.count(7);var r=0L;for(i in 0..<digits) r=r or a[i];return r==0L }
    fun odd(a: Region,audit: Audit?=null): Boolean { audit?.count(8);return a[0] and 1L!=0L }
    fun even(a: Region,audit: Audit?=null): Boolean { audit?.count(9);return a[0] and 1L==0L }

    fun mulAdd(a: Region,b: Long,top: Long,c: Region,audit: Audit?=null): Long {
        audit?.count(0)
        if(bits==64) return top+CryptoMontgomery.addMultiply(c.data,c.offset,a.data,a.offset,digits,b)
        var v=0L
        for(i in 0..<digits) {
            v+=c[i];v+=a[i]*b;c[i]=v and mask;v=v ushr bits
        }
        return (top+v) and mask
    }
    private fun difference(a: Long,b: Long,borrow: Long): Pair<Long,Long> {
        val d=a-b-borrow
        val next=if(bits==32) -(d shr 32) else ((a.inv() and b) or ((a xor b).inv() and d)) ushr 63
        return (d and mask) to next
    }
    fun subTop(topA: Long,a: Region,topC: Long,c: Region,audit: Audit?=null): Long {
        audit?.count(1);var borrow=0L
        for(i in 0..<digits) { val d=difference(c[i],a[i],borrow);c[i]=d.first;borrow=d.second }
        return (topC-topA-borrow) and mask
    }
    fun subM(mod: Region,top: Long,c: Region,take: Long,audit: Audit?=null): Long {
        audit?.count(2);var borrow=0L
        for(i in 0..<digits) { val d=difference(c[i],mod[i] and take,borrow);c[i]=d.first;borrow=d.second }
        return (top-borrow) and mask
    }
    fun addM(mod: Region,top: Long,c: Region,take: Long,audit: Audit?=null): Long {
        audit?.count(3);var v=0L
        for(i in 0..<digits) {
            val a=c[i];val b=mod[i] and take;val first=a+b;val sum=first+v
            v=if(bits==32) sum ushr 32 else carry(a,b,first) or carry(first,v,sum)
            c[i]=sum and mask
        }
        return (top+v) and mask
    }
    fun shiftLeft(a: Region,shift: Int,b: Region,audit: Audit?=null): Long {
        audit?.count(10);val n=shift%bits;val top=a[digits-1]
        for(i in digits-1 downTo 1) b[i]=((a[i] shl n) or (a[i-1] ushr (bits-n))) and mask
        b[0]=(a[0] shl n) and mask
        return if(n==0) 0 else top ushr (bits-n)
    }
    fun shiftRight(a: Region,shift: Int,b: Region,audit: Audit?=null) {
        audit?.count(11);val n=shift%bits
        for(i in 0..<digits-1) b[i]=((a[i] ushr n) or (a[i+1] shl (bits-n))) and mask
        b[digits-1]=a[digits-1] ushr n
    }
    fun shiftRightOne(a: Region,high: Int,b: Region,audit: Audit?=null) {
        audit?.count(12)
        for(i in 0..<digits-1) b[i]=((a[i] ushr 1) or (a[i+1] shl (bits-1))) and mask
        b[digits-1]=((a[digits-1] ushr 1) or (high.toLong() shl (bits-1))) and mask
    }
    fun cmp(a: Region,b: Region,audit: Audit?=null): Int {
        audit?.count(13);var borrow=0L;var nz=0L
        for(i in 0..<digits) { val d=difference(a[i],b[i],borrow);nz=nz or nonzero(d.first);borrow=d.second }
        return (-borrow or nz).toInt()
    }
    fun sub(a: Region,b: Region,c: Region?,audit: Audit?=null): Int {
        audit?.count(14);var borrow=0L
        for(i in 0..<digits) { val d=difference(a[i],b[i],borrow);c?.set(i,d.first);borrow=d.second }
        return -borrow.toInt()
    }
    fun add(a: Region,b: Region,c: Region?,audit: Audit?=null): Int {
        audit?.count(15);var v=0L
        for(i in 0..<digits) {
            val av=a[i];val bv=b[i];val first=av+bv;val sum=first+v
            v=if(bits==32) sum ushr 32 else carry(av,bv,first) or carry(first,v,sum)
            c?.set(i,sum and mask)
        }
        return v.toInt()
    }
    fun addDigit(a: Region,d: Long,b: Region?,audit: Audit?=null): Int {
        audit?.count(16);var v=d
        for(i in 0..<digits) {
            val av=a[i];val sum=av+v
            v=if(bits==32) sum ushr 32 else carry(av,v,sum)
            b?.set(i,sum and mask)
        }
        return v.toInt()
    }
    fun mod(mod: Region,input: Region,out: Region,audit: Audit?=null) {
        audit?.count(17)
        if(out.data!==input.data || out.offset!=input.offset) out.copyFrom(input,digits)
        val borrow=subM(mod,0,out,mask,audit);addM(mod,0,out,borrow,audit)
    }
    fun modMul(mod: Region,a: Region,topB: Long,b: Region,c: Region,audit: Audit?=null) {
        audit?.count(18);val tmp=Region(LongArray(digits*2+1));var top=0L
        for(i in 0..<digits) {
            if(i!=0) tmp[i+digits-1]=top
            top=mulAdd(a,b[i],0,tmp.at(i),audit)
        }
        tmp[2*digits-1]=top;top=mulAdd(a,topB,0,tmp.at(digits),audit)
        for(i in digits downTo 0) {
            val reducer=fresh();var tr=mulAdd(mod,top,0,reducer,audit)
            if(bits>32) tr+=mulAdd(mod,top ushr 32,0,reducer,audit)
            top=subTop(tr,reducer,top,tmp.at(i),audit)
            val correction=top ushr (bits-1);audit?.let { it.quotientCorrections+=correction }
            top=addM(mod,top,tmp.at(i),(-correction) and mask,audit)
            check(top.toULong()<=1uL)
            top=subM(mod,top,tmp.at(i),(top-1).inv() and mask,audit)
            check(top==0L)
            top=tmp[i+digits-1]
        }
        val borrow=subM(mod,0,tmp,mask,audit);addM(mod,0,tmp,borrow,audit)
        c.copyFrom(tmp,digits)
    }
    fun inverseVarTime(mod: Region,a: Region,b: Region,audit: Audit?=null) {
        audit?.count(19);val r=fresh();val s=fresh().also { it[0]=1 };val u=mod.copy(digits);val v=a.copy(digits)
        var steps=0
        while(true) {
            steps++;audit?.count(27)
            if(audit!=null && audit.inverseLimit>0 && steps>audit.inverseLimit) {
                audit.stopped=true
                audit.inverseState=listOf(r,s,u,v).flatMap { x->(0..<digits).map { x[it] } }.toLongArray()
                throw ObservationStopped()
            }
            if(even(u,audit)) {
                shiftRightOne(u,0,u,audit)
                if(even(r,audit)) shiftRightOne(r,0,r,audit) else shiftRightOne(r,add(r,mod,r,audit),r,audit)
            } else if(even(v,audit)) {
                shiftRightOne(v,0,v,audit)
                if(even(s,audit)) shiftRightOne(s,0,s,audit) else shiftRightOne(s,add(s,mod,s,audit),s,audit)
            } else if(sub(v,u,null,audit)==0) {
                sub(v,u,v,audit)
                if(sub(s,r,s,audit)!=0) add(s,mod,s,audit)
                if(zero(v,audit)) break
            } else {
                sub(u,v,u,audit)
                if(sub(r,s,r,audit)!=0) add(r,mod,r,audit)
            }
        }
        mod(mod,r,b,audit)
    }
    fun valid(x: Region,y: Region,audit: Audit?=null): Boolean {
        audit?.count(20)
        if(cmp(p,x,audit)<=0 || cmp(p,y,audit)<=0 || zero(y,audit)) return false
        val y2=fresh();val x3=fresh()
        modMul(p,y,0,y,y2,audit);modMul(p,x,0,x,x3,audit);modMul(p,x,0,x3,x3,audit)
        repeat(3) { if(sub(x3,x,x3,audit)!=0) add(x3,p,x3,audit) }
        if(add(x3,b,x3,audit)!=0) sub(x3,p,x3,audit)
        mod(p,x3,x3,audit)
        return cmp(y2,x3,audit)==0
    }
    fun fromBinary(src: CryptoByteSpan,dst: Region,audit: Audit?=null) {
        audit?.count(21);var at=0
        for(i in digits-1 downTo 0) {
            var d=0L
            for(n in bits-8 downTo 0 step 8) d=d or ((src[at++].toLong() and 255L) shl n)
            dst[i]=d
        }
    }
    fun toBinary(src: Region,dst: CryptoByteSpan,audit: Audit?=null) {
        audit?.count(22);var at=0
        for(i in digits-1 downTo 0) for(n in bits-8 downTo 0 step 8) dst[at++]=(src[i] ushr n).toByte()
    }
    fun modAdd(mod: Region,a: Region,b: Region,c: Region,audit: Audit?=null) {
        audit?.count(23);var top=add(a,b,c,audit).toLong()
        top=subM(mod,top,c,mask,audit)
        top=subM(mod,top,c,((top ushr (bits-1))-1) and mask,audit)
        addM(mod,0,c,top,audit)
    }
    fun modSub(mod: Region,a: Region,b: Region,c: Region,audit: Audit?=null) {
        audit?.count(24);var top=sub(a,b,c,audit).toLong() and mask
        top=addM(mod,top,c,((top ushr (bits-1))-1).inv() and mask,audit)
        top=subM(mod,top,c,((top ushr (bits-1))-1) and mask,audit)
        addM(mod,0,c,top,audit)
    }
    fun montMul(a: Region,b: Region,c: Region,audit: Audit?=null) {
        audit?.count(25);val accum=Region(LongArray(digits+1));var hiCarry=0L
        for(i in 0..<digits) {
            var mand=a[i];var chain=0L
            for(j in 0..<digits) {
                val product=mand*b[j];val first=product+accum[j];val sum=first+chain
                chain=if(bits==32) sum ushr 32 else Math.unsignedMultiplyHigh(mand,b[j])+carry(product,accum[j],first)+carry(first,chain,sum)
                accum[j]=sum and mask
            }
            accum[digits]=chain;mand=accum[0]*factor and mask;chain=0
            for(j in 0..<digits) {
                val product=mand*n[j];val first=product+accum[j];val sum=first+chain
                chain=if(bits==32) sum ushr 32 else Math.unsignedMultiplyHigh(mand,n[j])+carry(product,accum[j],first)+carry(first,chain,sum)
                if(j!=0) accum[j-1]=sum and mask
            }
            val first=chain+accum[digits];val sum=first+hiCarry
            hiCarry=if(bits==32) sum ushr 32 else carry(chain,accum[digits],first) or carry(first,hiCarry,sum)
            accum[digits-1]=sum and mask
        }
        c.copyFrom(accum,digits)
        val borrow=sub(c,n,c,audit)
        addM(n,0,c,(borrow.toLong()+hiCarry) and mask,audit)
    }
    fun inverseSafe(a: Region,b: Region,audit: Audit?=null) {
        audit?.count(26)
        val x1=fresh();val x10=fresh();val x11=fresh();val x101=fresh();val x111=fresh();val x1010=fresh();val x1111=fresh()
        val x10101=fresh();val x101010=fresh();val x101111=fresh();val x6=fresh();val x8=fresh();val x16=fresh();val x32=fresh()
        fun thenAdd(a: Region,nth: Int,x: Region,out: Region) {
            montMul(a,a,out,audit);repeat(nth-1) { montMul(out,out,out,audit) };montMul(out,x,out,audit)
        }
        montMul(a,r2,x1,audit)
        montMul(x1,x1,x10,audit);montMul(x10,x1,x11,audit);montMul(x10,x11,x101,audit)
        montMul(x10,x101,x111,audit);montMul(x101,x101,x1010,audit);montMul(x101,x1010,x1111,audit)
        thenAdd(x1010,1,x1,x10101);montMul(x10101,x10101,x101010,audit)
        montMul(x101,x101010,x101111,audit);montMul(x10101,x101010,x6,audit)
        thenAdd(x6,2,x11,x8);thenAdd(x8,8,x8,x16);thenAdd(x16,16,x16,x32)
        thenAdd(x32,64,x32,b);thenAdd(b,32,x32,b);thenAdd(b,6,x101111,b)
        thenAdd(b,5,x111,b);thenAdd(b,4,x11,b);thenAdd(b,5,x1111,b)
        thenAdd(b,5,x10101,b);thenAdd(b,4,x101,b);thenAdd(b,3,x101,b);thenAdd(b,3,x101,b)
        thenAdd(b,5,x111,b);thenAdd(b,9,x101111,b);thenAdd(b,6,x1111,b)
        thenAdd(b,2,x1,b);thenAdd(b,5,x1,b);thenAdd(b,6,x1111,b)
        thenAdd(b,5,x111,b);thenAdd(b,4,x111,b);thenAdd(b,5,x111,b);thenAdd(b,5,x101,b)
        thenAdd(b,3,x11,b);thenAdd(b,10,x101111,b);thenAdd(b,2,x11,b)
        thenAdd(b,5,x11,b);thenAdd(b,5,x11,b);thenAdd(b,3,x1,b);thenAdd(b,7,x10101,b);thenAdd(b,6,x1111,b)
        montMul(b,one,b,audit)
    }
}
