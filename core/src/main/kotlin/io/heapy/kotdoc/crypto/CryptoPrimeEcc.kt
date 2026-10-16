// crypton 2.0.1 cbits/crypton_ecc.c; package notice: licenses/crypton-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto

/** Fixed-width prime-field engine. The caller validates the curve and input point. */
internal object CryptoPrimeEcc {
    class Audit(val failAllocation: Int = 0) {
        val operations = LongArray(10)
        var tableAddressHash = 0L
        val allocations = ArrayList<Long>()
        val frees = ArrayList<Int>()
        val cleared = ArrayList<Boolean>()
    }

    private class Region(val data: LongArray, val offset: Int) {
        operator fun get(i: Int) = data[offset + i]
        operator fun set(i: Int, v: Long) { data[offset + i] = v }
        fun at(i: Int) = Region(data, offset + i)
        fun zero(n: Int) = data.fill(0, offset, offset + n)
        fun copyFrom(other: Region, n: Int) = other.data.copyInto(data, offset, other.offset, other.offset + n)
    }
    private class Point(val region: Region, n: Int) {
        val x = region; val y = region.at(n); val z = region.at(2 * n)
    }
    private class Memory(val audit: Audit?) {
        var lastId = 0
        fun longArray(n: Int): LongArray {
            audit?.allocations?.add(n.toLong() * 8)
            lastId = audit?.allocations?.size ?: 0
            if (lastId != 0 && lastId == audit?.failAllocation) throw OutOfMemoryError("controlled allocation failure")
            return LongArray(n)
        }
        fun byteArray(n: Int): ByteArray {
            audit?.allocations?.add(n.toLong())
            lastId = audit?.allocations?.size ?: 0
            if (lastId != 0 && lastId == audit?.failAllocation) throw OutOfMemoryError("controlled allocation failure")
            return ByteArray(n)
        }
        fun freed(id: Int, clear: Boolean) { audit?.frees?.add(id); audit?.cleared?.add(clear) }
    }

    private class Context(val space: LongArray, val bytes: ByteArray, val spaceId: Int, val bytesId: Int, val n: Int, val plen: Int, val memory: Memory) {
        val p = Region(space, 0); val a = p.at(n); val b3 = a.at(n); val r2 = b3.at(n); val one = r2.at(n); val zero = one.at(n)
        val acc = Point(zero.at(n), n); val sel = Point(acc.region.at(3 * n), n); val tmp = Point(sel.region.at(3 * n), n)
        val s = tmp.region.at(3 * n); val s2 = s.at(n); val montT = s.at(2 * n)
        val work = Array(9) { montT.at((2 + it) * n) }
        val table = work[0].at(9 * n)
        val t0 = work[0]; val t1 = work[1]; val t2 = work[2]; val t3 = work[3]; val t4 = work[4]; val t5 = work[5]
        val x3 = work[6]; val y3 = work[7]; val z3 = work[8]
        var n0 = 0L
        var aZero = false
        var aMinus3 = false
        fun count(i: Int) { memory.audit?.operations?.let { it[i]++ } }
        fun montMul(r: Region, x: Region, y: Region) = CryptoMontgomery.multiply(r.data,r.offset,x.data,x.offset,y.data,y.offset,p.data,p.offset,n0,n,montT.data,montT.offset)
        fun mul(r: Region, x: Region, y: Region) { count(0); montMul(r,x,y) }
        fun sqr(r: Region, x: Region) { count(1); CryptoMontgomery.square(r.data,r.offset,x.data,x.offset,p.data,p.offset,n0,n,montT.data,montT.offset) }
        fun add(r: Region, x: Region, y: Region) {
            count(2)
            val carry = CryptoMontgomery.add(r.data,r.offset,x.data,x.offset,y.data,y.offset,n)
            val borrow = CryptoMontgomery.subtract(s.data,s.offset,r.data,r.offset,p.data,p.offset,n)
            CryptoMontgomery.select(r.data,r.offset,s.data,s.offset,r.data,r.offset,(carry or (borrow xor 1L)) and 1L,n)
        }
        fun sub(r: Region, x: Region, y: Region) {
            count(3)
            val borrow = CryptoMontgomery.subtract(r.data,r.offset,x.data,x.offset,y.data,y.offset,n)
            CryptoMontgomery.add(s.data,s.offset,r.data,r.offset,p.data,p.offset,n)
            CryptoMontgomery.select(r.data,r.offset,s.data,s.offset,r.data,r.offset,borrow,n)
        }
        fun neg(r: Region, x: Region) { count(4); sub(r,zero,x) }
        fun mulA(r: Region, x: Region) {
            count(5)
            if (aZero) r.zero(n)
            else if (aMinus3) { add(s2,x,x); add(s2,s2,x); neg(r,s2) }
            else mul(r,a,x)
        }
        fun toMont(r: Region, x: Region) = montMul(r,x,r2)
        // Preserve the source's actual representation conversion, including its one buffer.
        fun fromMont(r: Region, x: Region) = montMul(r,x,one)
        fun import(r: Region, value: CryptoByteSpan) = CryptoMontgomery.fromBigEndian(r.data,r.offset,n,value)
        fun export(out: CryptoByteSpan, x: Region) = CryptoMontgomery.toBigEndian(out,x.data,x.offset,n)
        fun free() {
            space.fill(0); memory.freed(spaceId,space.all { it == 0L })
            bytes.fill(0); memory.freed(bytesId,bytes.all { it == 0.toByte() })
        }
        fun pointFrom(r: Point, px: CryptoByteSpan, py: CryptoByteSpan): Int {
            if (import(tmp.x,px) != 0) return -1
            toMont(r.x,tmp.x)
            if (import(tmp.x,py) != 0) return -1
            toMont(r.y,tmp.x); r.z.copyFrom(one,n)
            return 0
        }
        fun pointTo(outx: CryptoByteSpan, outy: CryptoByteSpan, pt: Point, modulus: CryptoByteSpan): Int {
            var nonzero = 0L
            for (i in 0..<n) nonzero = nonzero or pt.z[i]
            if (nonzero == 0L) return 1
            val zbytes = CryptoByteSpan(bytes,0,plen); val pm2 = CryptoByteSpan(bytes,plen,plen)
            fromMont(tmp.x,pt.z); export(zbytes,tmp.x)
            sel.x.zero(n); sel.x[0] = 2L
            CryptoMontgomery.subtract(sel.x.data,sel.x.offset,p.data,p.offset,sel.x.data,sel.x.offset,n)
            export(pm2,sel.x)
            var allocated: LongArray? = null
            var allocationId = 0
            val status = CryptoMontgomery.powSpans(zbytes,zbytes,pm2,modulus,allocate = {
                val data = memory.longArray(it); allocationId = memory.lastId; allocated = data; data
            })
            allocated?.let { memory.freed(allocationId,it.all { v -> v == 0L }) }
            if (status != 0 || import(tmp.x,zbytes) != 0) return -1
            toMont(sel.x,tmp.x)
            mul(tmp.x,pt.x,sel.x); fromMont(tmp.y,tmp.x); export(outx,tmp.y)
            mul(tmp.x,pt.y,sel.x); fromMont(tmp.y,tmp.x); export(outy,tmp.y)
            return 0
        }
        fun pointAdd(r: Point, x: Point, y: Point) {
            count(6)
            mul(t0,x.x,y.x); mul(t1,x.y,y.y); mul(t2,x.z,y.z)
            add(t3,x.x,x.y); add(t4,y.x,y.y); mul(t3,t3,t4); add(t4,t0,t1); sub(t3,t3,t4)
            add(t4,x.x,x.z); add(t5,y.x,y.z); mul(t4,t4,t5); add(t5,t0,t2); sub(t4,t4,t5)
            add(t5,x.y,x.z); add(x3,y.y,y.z); mul(t5,t5,x3); add(x3,t1,t2); sub(t5,t5,x3)
            mulA(z3,t4); mul(x3,b3,t2); add(z3,x3,z3); sub(x3,t1,z3); add(z3,t1,z3); mul(y3,x3,z3)
            add(t1,t0,t0); add(t1,t1,t0); mulA(t2,t2); mul(t4,b3,t4); add(t1,t1,t2)
            sub(t2,t0,t2); mulA(t2,t2); add(t4,t4,t2); mul(t0,t1,t4); add(y3,y3,t0)
            mul(t0,t5,t4); mul(x3,t3,x3); sub(x3,x3,t0); mul(t0,t3,t1); mul(t1,t5,z3); add(z3,t1,t0)
            r.x.copyFrom(x3,n); r.y.copyFrom(y3,n); r.z.copyFrom(z3,n)
        }
        fun pointDouble(r: Point, x: Point) {
            count(7)
            sqr(t0,x.x); sqr(t1,x.y); sqr(t2,x.z); mul(t3,x.x,x.y); add(t3,t3,t3)
            mul(z3,x.x,x.z); add(z3,z3,z3); mulA(x3,z3); mul(y3,b3,t2); add(y3,x3,y3)
            sub(x3,t1,y3); add(y3,t1,y3); mul(y3,x3,y3); mul(x3,t3,x3); mul(z3,b3,z3)
            mulA(t2,t2); sub(t3,t0,t2); mulA(t3,t3); add(t3,t3,z3); add(z3,t0,t0)
            add(t0,z3,t0); add(t0,t0,t2); mul(t0,t0,t3); add(y3,y3,t0)
            mul(t2,x.y,x.z); add(t2,t2,t2); mul(t0,t2,t3); sub(x3,x3,t0)
            mul(z3,t2,t1); add(z3,z3,z3); add(z3,z3,z3)
            r.x.copyFrom(x3,n); r.y.copyFrom(y3,n); r.z.copyFrom(z3,n)
        }
        fun select(r: Point, table: Region, root: Int, w: Long) {
            count(8); r.region.zero(3*n)
            for (j in 0..<16) {
                val mask = CryptoMontgomery.equalityMask(j.toLong(),w)
                for (l in 0..<3*n) {
                    memory.audit?.let { it.operations[9]++; it.tableAddressHash = (it.tableAddressHash * 0x100000001b3L) xor (table.offset-root+3*j*n+l).toLong() }
                    r.region[l] = r.region[l] or (table[3*j*n+l] and mask)
                }
            }
        }
    }

    private fun context(a: CryptoByteSpan, b: CryptoByteSpan, p: CryptoByteSpan, memory: Memory): Context? {
        val plen = p.size; val n = (plen+7)/8
        if (plen == 0 || n == 0 || p[plen-1].toInt() and 1 == 0) return null
        val space = try { memory.longArray(76*n) } catch (_: OutOfMemoryError) { null }
        val sid = memory.lastId
        val bytes = try { memory.byteArray(2*plen) } catch (_: OutOfMemoryError) { null }
        val bid = memory.lastId
        if (space == null || bytes == null) {
            space?.let { it.fill(0); memory.freed(sid,it.all { v -> v == 0L }) }
            bytes?.let { it.fill(0); memory.freed(bid,it.all { v -> v == 0.toByte() }) }
            return null
        }
        val c = Context(space,bytes,sid,bid,n,plen,memory)
        if (c.import(c.p,p) != 0) { c.free(); return null }
        CryptoMontgomery.r2(c.r2.data,c.r2.offset,c.p.data,c.p.offset,n,c.montT.data,c.montT.offset)
        c.n0 = CryptoMontgomery.n0(c.p[0]); c.one[0] = 1L; c.toMont(c.tmp.x,c.one); c.one.copyFrom(c.tmp.x,n)
        if (c.import(c.tmp.x,a) != 0) { c.free(); return null }
        var nonzero = 0L; var differs = 0L
        for (i in 0..<n) nonzero = nonzero or c.tmp.x[i]
        c.sel.x.zero(n); c.sel.x[0] = 3L
        CryptoMontgomery.subtract(c.sel.x.data,c.sel.x.offset,c.p.data,c.p.offset,c.sel.x.data,c.sel.x.offset,n)
        for (i in 0..<n) differs = differs or (c.tmp.x[i] xor c.sel.x[i])
        c.aZero = nonzero == 0L; c.aMinus3 = differs == 0L; c.toMont(c.a,c.tmp.x)
        if (c.import(c.tmp.x,b) != 0) { c.free(); return null }
        c.toMont(c.b3,c.tmp.x); c.add(c.tmp.x,c.b3,c.b3); c.add(c.b3,c.tmp.x,c.b3)
        return c
    }

    fun multiply(outx: CryptoByteSpan, outy: CryptoByteSpan, px: CryptoByteSpan, py: CryptoByteSpan, k: CryptoByteSpan, a: CryptoByteSpan, b: CryptoByteSpan, p: CryptoByteSpan, audit: Audit? = null): Int {
        if (k.size == 0) return -1
        val c = context(a,b,p,Memory(audit)) ?: return -1
        val n = c.n
        c.table.zero(3*n); c.table.at(n).copyFrom(c.one,n)
        if (c.pointFrom(Point(c.table.at(3*n),n),px,py) != 0) { c.free(); return -1 }
        for (i in 2..<16) c.pointAdd(Point(c.table.at(3*i*n),n),Point(c.table.at(3*(i-1)*n),n),Point(c.table.at(3*n),n))
        c.acc.region.copyFrom(c.table,3*n)
        for (i in k.size*2 downTo 1) {
            val nibble = i-1; val w = (k[k.size-1-nibble/2].toLong() ushr (4*(nibble%2))) and 15L
            repeat(4) { c.pointDouble(c.acc,c.acc) }
            c.select(c.sel,c.table,c.table.offset,w); c.pointAdd(c.acc,c.acc,c.sel)
        }
        val result = c.pointTo(outx,outy,c.acc,p); c.free(); return result
    }

    fun tableSize(plen: UInt, klen: UInt): UInt {
        val n = (plen+7u)/8u
        return if (plen == 0u || klen == 0u || n == 0u) 0u else klen*2u*16u*3u*n*8u
    }

    fun buildTable(table: LongArray, px: CryptoByteSpan, py: CryptoByteSpan, klen: Int, a: CryptoByteSpan, b: CryptoByteSpan, p: CryptoByteSpan, audit: Audit? = null, tableOffset: Int = 0): Int {
        if (klen == 0) return -1
        val c = context(a,b,p,Memory(audit)) ?: return -1
        val n = c.n
        if (c.pointFrom(c.acc,px,py) != 0) { c.free(); return -1 }
        for (i in 0..<klen*2) {
            val slot = Region(table,tableOffset+i*16*3*n)
            slot.zero(3*n); slot.at(n).copyFrom(c.one,n); slot.at(3*n).copyFrom(c.acc.region,3*n)
            for (j in 2..<16) c.pointAdd(Point(slot.at(3*j*n),n),Point(slot.at(3*(j-1)*n),n),c.acc)
            repeat(4) { c.pointDouble(c.acc,c.acc) }
        }
        c.free(); return 0
    }

    fun multiplyTable(outx: CryptoByteSpan, outy: CryptoByteSpan, table: LongArray, k: CryptoByteSpan, a: CryptoByteSpan, b: CryptoByteSpan, p: CryptoByteSpan, audit: Audit? = null, tableOffset: Int = 0): Int {
        if (k.size == 0) return -1
        val c = context(a,b,p,Memory(audit)) ?: return -1
        val n = c.n
        c.acc.region.zero(3*n); c.acc.y.copyFrom(c.one,n)
        for (i in 0..<k.size*2) {
            val w = (k[k.size-1-i/2].toLong() ushr (4*(i%2))) and 15L
            c.select(c.sel,Region(table,tableOffset+i*16*3*n),tableOffset,w); c.pointAdd(c.acc,c.acc,c.sel)
        }
        val result = c.pointTo(outx,outy,c.acc,p); c.free(); return result
    }
}
