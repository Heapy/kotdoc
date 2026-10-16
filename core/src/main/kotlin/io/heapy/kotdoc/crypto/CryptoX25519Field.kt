// Donna X25519, crypton 2.0.1; Adam Langley / Google / Daniel J. Bernstein.
// Notices: licenses/curve25519-donna-Google-BSD-3-Clause.txt,
// licenses/curve25519-donna-public-domain.txt.
package io.heapy.kotdoc.crypto

/** Exact redundant field profiles; source signed-C assumptions are recorded with fixtures. */
internal class CryptoX25519Field(val bits: Int) {
    init { require(bits==32 || bits==64) }
    val limbs=if(bits==32) 10 else 5
    typealias Region=CryptoP256Words.Region
    typealias Wide=CryptoP256Field.Wide
    private val mask51=0x7ffffffffffffL
    fun sum(out: Region,a: Region) { for(i in 0..<limbs) out[i]=out[i]+a[i] }
    fun difference(out: Region,a: Region) {
        if(bits==32) for(i in 0..<10) out[i]=a[i]-out[i]
        else for(i in 0..<5) out[i]=a[i]+(if(i==0)(1L shl 54)-152 else (1L shl 54)-8)-out[i]
    }
    fun scalar(out: Region,a: Region,n: Long) {
        if(bits==32) for(i in 0..<10) out[i]=a[i]*n
        else {
            var carry=Wide.of(0)
            for(i in 0..<5) {
                carry=Wide.product(a[i],n)+Wide.of((carry ushr 51).low)
                out[i]=carry.low and mask51
            }
            out[0]=out[0]+((carry ushr 51).low*19)
        }
    }
    fun div26(v: Long): Long {val sign=((v ushr 32).toInt() shr 31);return (v+((sign.toLong()and 0xffffffffL) ushr 6)) shr 26}
    fun div25(v: Long): Long {val sign=((v ushr 32).toInt() shr 31);return (v+((sign.toLong()and 0xffffffffL) ushr 7)) shr 25}
    fun reduceDegree(out: Region) {
        for(i in 8 downTo 0) {out[i]=out[i]+(out[i+10] shl 4);out[i]=out[i]+(out[i+10] shl 1);out[i]=out[i]+out[i+10]}
    }
    fun reduceCoefficients(out: Region) {
        out[10]=0
        for(i in 0..<10 step 2) {
            var over=div26(out[i]);out[i]=out[i]-(over shl 26);out[i+1]=out[i+1]+over
            over=div25(out[i+1]);out[i+1]=out[i+1]-(over shl 25);out[i+2]=out[i+2]+over
        }
        out[0]=out[0]+(out[10] shl 4);out[0]=out[0]+(out[10] shl 1);out[0]=out[0]+out[10];out[10]=0
        val over=div26(out[0]);out[0]=out[0]-(over shl 26);out[1]=out[1]+over
    }
    private fun reduced64(t: Array<Wide>): LongArray {
        val r=LongArray(5)
        for(i in 0..<4) {r[i]=t[i].low and mask51;t[i+1]=t[i+1]+Wide.of((t[i] ushr 51).low)}
        r[4]=t[4].low and mask51
        r[0]+=(t[4] ushr 51).low*19
        var c=r[0] ushr 51;r[0]=r[0]and mask51;r[1]+=c
        c=r[1] ushr 51;r[1]=r[1]and mask51;r[2]+=c
        return r
    }
    fun multiply(out: Region,a: Region,b: Region) {
        if(bits==32) {
            val t=Region(LongArray(19));product(t,a,b);reduceDegree(t);reduceCoefficients(t);out.copyFrom(t,10)
        } else {
            val r=LongArray(5){b[it]};val s=LongArray(5){a[it]};val t=Array(5){Wide.of(0)}
            for(i in 0..<5) for(j in 0..<5) {
                val k=i+j
                t[k%5]=t[k%5]+Wide.product(if(k>=5) r[i]*19 else r[i],s[j])
            }
            val result=reduced64(t);for(i in 0..<5)out[i]=result[i]
        }
    }
    fun square(out: Region,a: Region,count: Long=1) {
        if(bits==32) {
            val t=Region(LongArray(19));squareInner(t,a);reduceDegree(t);reduceCoefficients(t);out.copyFrom(t,10)
        } else {
            var r=LongArray(5){a[it]};var remaining=count
            do {
                val d0=r[0]*2;val d1=r[1]*2;val d2=r[2]*2*19;val d419=r[4]*19;val d4=d419*2
                val t=arrayOf(
                    Wide.product(r[0],r[0])+Wide.product(d4,r[1])+Wide.product(d2,r[3]),
                    Wide.product(d0,r[1])+Wide.product(d4,r[2])+Wide.product(r[3],r[3]*19),
                    Wide.product(d0,r[2])+Wide.product(r[1],r[1])+Wide.product(d4,r[3]),
                    Wide.product(d0,r[3])+Wide.product(d1,r[2])+Wide.product(r[4],d419),
                    Wide.product(d0,r[4])+Wide.product(d1,r[3])+Wide.product(r[2],r[2]))
                r=reduced64(t);remaining--
            }while(remaining!=0L)
            for(i in 0..<5)out[i]=r[i]
        }
    }
    fun load(bytes: CryptoByteSpan): Long {var v=0L;for(i in 0..<8)v=v or ((bytes.data[bytes.offset+i].toLong()and 255) shl(8*i));return v}
    fun store(bytes: CryptoByteSpan,value: Long) {for(i in 0..<8)bytes.data[bytes.offset+i]=(value ushr(8*i)).toByte()}
    fun expand(out: Region,bytes: CryptoByteSpan) {
        if(bits==64) {
            val starts=intArrayOf(0,6,12,19,24);val shifts=intArrayOf(0,3,6,1,12)
            for(i in 0..<5)out[i]=(load(CryptoByteSpan(bytes.data,bytes.offset+starts[i],8)) ushr shifts[i])and mask51
        } else {
            val starts=intArrayOf(0,3,6,9,12,16,19,22,25,28);val shifts=intArrayOf(0,2,3,5,6,0,1,3,4,6)
            for(i in 0..<10) {var v=0L;for(j in 0..<4)v=v or ((bytes.data[bytes.offset+starts[i]+j].toLong()and 255) shl(8*j));out[i]=(v shr shifts[i])and(if(i%2==0)0x3ffffffL else 0x1ffffffL)}
        }
    }
    fun eq32(aa: Int,b: Int): Int {var a=(aa xor b).inv();for(n in intArrayOf(16,8,4,2,1))a=a and(a shl n);return a shr 31}
    fun gte32(a: Int,b: Int)=((a-b) shr 31).inv()
    fun contract(bytes: CryptoByteSpan,a: Region) {
        if(bits==64) {
            val t=Array(5){Wide.of(a[it])};val mask=Wide.of(mask51)
            fun carry() {for(i in 0..<4){t[i+1]=t[i+1]+(t[i] ushr 51);t[i]=t[i]and mask};t[0]=t[0]+Wide.product((t[4] ushr 51).low,19);t[4]=t[4]and mask}
            carry();carry();t[0]=t[0]+Wide.of(19);carry()
            t[0]=t[0]+Wide.of((1L shl 51)-19);for(i in 1..<5)t[i]=t[i]+Wide.of((1L shl 51)-1)
            for(i in 0..<4){t[i+1]=t[i+1]+(t[i] ushr 51);t[i]=t[i]and mask};t[4]=t[4]and mask
            val values=longArrayOf(t[0].low or(t[1].low shl 51),(t[1].low ushr 13)or(t[2].low shl 38),(t[2].low ushr 26)or(t[3].low shl 25),(t[3].low ushr 39)or(t[4].low shl 12))
            for(i in 0..<4)store(CryptoByteSpan(bytes.data,bytes.offset+8*i,8),values[i])
        } else {
            val input=IntArray(10){a[it].toInt()}
            fun borrow(i: Int,width: Int) {val mask=input[i] shr 31;val carry=-((input[i]and mask)shr width);input[i]+=carry shl width;input[i+1]-=carry}
            repeat(2) {for(i in 0..<9)borrow(i,if(i%2==0)26 else 25);val mask=input[9]shr 31;val carry=-((input[9]and mask)shr 25);input[9]+=carry shl 25;input[0]-=carry*19}
            borrow(0,26)
            repeat(2) {for(i in 0..<9){val w=if(i%2==0)26 else 25;val carry=input[i]shr w;input[i]=input[i]and(if(i%2==0)0x3ffffff else 0x1ffffff);input[i+1]+=carry};val carry=input[9]shr 25;input[9]=input[9]and 0x1ffffff;input[0]+=19*carry}
            var mask=gte32(input[0],0x3ffffed);for(i in 1..<10)mask=mask and eq32(input[i],if(i%2==1)0x1ffffff else 0x3ffffff)
            input[0]-=mask and 0x3ffffed;for(i in 1..<10)input[i]-=mask and(if(i%2==1)0x1ffffff else 0x3ffffff)
            val shifts=intArrayOf(0,2,3,5,6,0,1,3,4,6);for(i in 0..<10)input[i]=input[i]shl shifts[i]
            val starts=intArrayOf(0,3,6,9,12,16,19,22,25,28);bytes.data[bytes.offset]=0;bytes.data[bytes.offset+16]=0
            for(i in 0..<10){val s=bytes.offset+starts[i];bytes.data[s]=(bytes.data[s].toInt()or(input[i]and 255)).toByte();for(j in 1..<4)bytes.data[s+j]=(input[i]shr(8*j)).toByte()}
        }
    }

    fun product(out: Region,a: Region,b: Region) {
  out[0] =       b[0].toInt().toLong() * a[0].toInt().toLong()
  out[1] =       b[0].toInt().toLong() * a[1].toInt().toLong() +
                    b[1].toInt().toLong() * a[0].toInt().toLong()
  out[2] =  2 *  b[1].toInt().toLong() * a[1].toInt().toLong() +
                    b[0].toInt().toLong() * a[2].toInt().toLong() +
                    b[2].toInt().toLong() * a[0].toInt().toLong()
  out[3] =       b[1].toInt().toLong() * a[2].toInt().toLong() +
                    b[2].toInt().toLong() * a[1].toInt().toLong() +
                    b[0].toInt().toLong() * a[3].toInt().toLong() +
                    b[3].toInt().toLong() * a[0].toInt().toLong()
  out[4] =       b[2].toInt().toLong() * a[2].toInt().toLong() +
               2 * (b[1].toInt().toLong() * a[3].toInt().toLong() +
                    b[3].toInt().toLong() * a[1].toInt().toLong()) +
                    b[0].toInt().toLong() * a[4].toInt().toLong() +
                    b[4].toInt().toLong() * a[0].toInt().toLong()
  out[5] =       b[2].toInt().toLong() * a[3].toInt().toLong() +
                    b[3].toInt().toLong() * a[2].toInt().toLong() +
                    b[1].toInt().toLong() * a[4].toInt().toLong() +
                    b[4].toInt().toLong() * a[1].toInt().toLong() +
                    b[0].toInt().toLong() * a[5].toInt().toLong() +
                    b[5].toInt().toLong() * a[0].toInt().toLong()
  out[6] =  2 * (b[3].toInt().toLong() * a[3].toInt().toLong() +
                    b[1].toInt().toLong() * a[5].toInt().toLong() +
                    b[5].toInt().toLong() * a[1].toInt().toLong()) +
                    b[2].toInt().toLong() * a[4].toInt().toLong() +
                    b[4].toInt().toLong() * a[2].toInt().toLong() +
                    b[0].toInt().toLong() * a[6].toInt().toLong() +
                    b[6].toInt().toLong() * a[0].toInt().toLong()
  out[7] =       b[3].toInt().toLong() * a[4].toInt().toLong() +
                    b[4].toInt().toLong() * a[3].toInt().toLong() +
                    b[2].toInt().toLong() * a[5].toInt().toLong() +
                    b[5].toInt().toLong() * a[2].toInt().toLong() +
                    b[1].toInt().toLong() * a[6].toInt().toLong() +
                    b[6].toInt().toLong() * a[1].toInt().toLong() +
                    b[0].toInt().toLong() * a[7].toInt().toLong() +
                    b[7].toInt().toLong() * a[0].toInt().toLong()
  out[8] =       b[4].toInt().toLong() * a[4].toInt().toLong() +
               2 * (b[3].toInt().toLong() * a[5].toInt().toLong() +
                    b[5].toInt().toLong() * a[3].toInt().toLong() +
                    b[1].toInt().toLong() * a[7].toInt().toLong() +
                    b[7].toInt().toLong() * a[1].toInt().toLong()) +
                    b[2].toInt().toLong() * a[6].toInt().toLong() +
                    b[6].toInt().toLong() * a[2].toInt().toLong() +
                    b[0].toInt().toLong() * a[8].toInt().toLong() +
                    b[8].toInt().toLong() * a[0].toInt().toLong()
  out[9] =       b[4].toInt().toLong() * a[5].toInt().toLong() +
                    b[5].toInt().toLong() * a[4].toInt().toLong() +
                    b[3].toInt().toLong() * a[6].toInt().toLong() +
                    b[6].toInt().toLong() * a[3].toInt().toLong() +
                    b[2].toInt().toLong() * a[7].toInt().toLong() +
                    b[7].toInt().toLong() * a[2].toInt().toLong() +
                    b[1].toInt().toLong() * a[8].toInt().toLong() +
                    b[8].toInt().toLong() * a[1].toInt().toLong() +
                    b[0].toInt().toLong() * a[9].toInt().toLong() +
                    b[9].toInt().toLong() * a[0].toInt().toLong()
  out[10] = 2 * (b[5].toInt().toLong() * a[5].toInt().toLong() +
                    b[3].toInt().toLong() * a[7].toInt().toLong() +
                    b[7].toInt().toLong() * a[3].toInt().toLong() +
                    b[1].toInt().toLong() * a[9].toInt().toLong() +
                    b[9].toInt().toLong() * a[1].toInt().toLong()) +
                    b[4].toInt().toLong() * a[6].toInt().toLong() +
                    b[6].toInt().toLong() * a[4].toInt().toLong() +
                    b[2].toInt().toLong() * a[8].toInt().toLong() +
                    b[8].toInt().toLong() * a[2].toInt().toLong()
  out[11] =      b[5].toInt().toLong() * a[6].toInt().toLong() +
                    b[6].toInt().toLong() * a[5].toInt().toLong() +
                    b[4].toInt().toLong() * a[7].toInt().toLong() +
                    b[7].toInt().toLong() * a[4].toInt().toLong() +
                    b[3].toInt().toLong() * a[8].toInt().toLong() +
                    b[8].toInt().toLong() * a[3].toInt().toLong() +
                    b[2].toInt().toLong() * a[9].toInt().toLong() +
                    b[9].toInt().toLong() * a[2].toInt().toLong()
  out[12] =      b[6].toInt().toLong() * a[6].toInt().toLong() +
               2 * (b[5].toInt().toLong() * a[7].toInt().toLong() +
                    b[7].toInt().toLong() * a[5].toInt().toLong() +
                    b[3].toInt().toLong() * a[9].toInt().toLong() +
                    b[9].toInt().toLong() * a[3].toInt().toLong()) +
                    b[4].toInt().toLong() * a[8].toInt().toLong() +
                    b[8].toInt().toLong() * a[4].toInt().toLong()
  out[13] =      b[6].toInt().toLong() * a[7].toInt().toLong() +
                    b[7].toInt().toLong() * a[6].toInt().toLong() +
                    b[5].toInt().toLong() * a[8].toInt().toLong() +
                    b[8].toInt().toLong() * a[5].toInt().toLong() +
                    b[4].toInt().toLong() * a[9].toInt().toLong() +
                    b[9].toInt().toLong() * a[4].toInt().toLong()
  out[14] = 2 * (b[7].toInt().toLong() * a[7].toInt().toLong() +
                    b[5].toInt().toLong() * a[9].toInt().toLong() +
                    b[9].toInt().toLong() * a[5].toInt().toLong()) +
                    b[6].toInt().toLong() * a[8].toInt().toLong() +
                    b[8].toInt().toLong() * a[6].toInt().toLong()
  out[15] =      b[7].toInt().toLong() * a[8].toInt().toLong() +
                    b[8].toInt().toLong() * a[7].toInt().toLong() +
                    b[6].toInt().toLong() * a[9].toInt().toLong() +
                    b[9].toInt().toLong() * a[6].toInt().toLong()
  out[16] =      b[8].toInt().toLong() * a[8].toInt().toLong() +
               2 * (b[7].toInt().toLong() * a[9].toInt().toLong() +
                    b[9].toInt().toLong() * a[7].toInt().toLong())
  out[17] =      b[8].toInt().toLong() * a[9].toInt().toLong() +
                    b[9].toInt().toLong() * a[8].toInt().toLong()
  out[18] = 2 *  b[9].toInt().toLong() * a[9].toInt().toLong()
    }

    fun squareInner(out: Region,a: Region) {
  out[0] =       a[0].toInt().toLong() * a[0].toInt().toLong()
  out[1] =  2 *  a[0].toInt().toLong() * a[1].toInt().toLong()
  out[2] =  2 * (a[1].toInt().toLong() * a[1].toInt().toLong() +
                    a[0].toInt().toLong() * a[2].toInt().toLong())
  out[3] =  2 * (a[1].toInt().toLong() * a[2].toInt().toLong() +
                    a[0].toInt().toLong() * a[3].toInt().toLong())
  out[4] =       a[2].toInt().toLong() * a[2].toInt().toLong() +
               4 *  a[1].toInt().toLong() * a[3].toInt().toLong() +
               2 *  a[0].toInt().toLong() * a[4].toInt().toLong()
  out[5] =  2 * (a[2].toInt().toLong() * a[3].toInt().toLong() +
                    a[1].toInt().toLong() * a[4].toInt().toLong() +
                    a[0].toInt().toLong() * a[5].toInt().toLong())
  out[6] =  2 * (a[3].toInt().toLong() * a[3].toInt().toLong() +
                    a[2].toInt().toLong() * a[4].toInt().toLong() +
                    a[0].toInt().toLong() * a[6].toInt().toLong() +
               2 *  a[1].toInt().toLong() * a[5].toInt().toLong())
  out[7] =  2 * (a[3].toInt().toLong() * a[4].toInt().toLong() +
                    a[2].toInt().toLong() * a[5].toInt().toLong() +
                    a[1].toInt().toLong() * a[6].toInt().toLong() +
                    a[0].toInt().toLong() * a[7].toInt().toLong())
  out[8] =       a[4].toInt().toLong() * a[4].toInt().toLong() +
               2 * (a[2].toInt().toLong() * a[6].toInt().toLong() +
                    a[0].toInt().toLong() * a[8].toInt().toLong() +
               2 * (a[1].toInt().toLong() * a[7].toInt().toLong() +
                    a[3].toInt().toLong() * a[5].toInt().toLong()))
  out[9] =  2 * (a[4].toInt().toLong() * a[5].toInt().toLong() +
                    a[3].toInt().toLong() * a[6].toInt().toLong() +
                    a[2].toInt().toLong() * a[7].toInt().toLong() +
                    a[1].toInt().toLong() * a[8].toInt().toLong() +
                    a[0].toInt().toLong() * a[9].toInt().toLong())
  out[10] = 2 * (a[5].toInt().toLong() * a[5].toInt().toLong() +
                    a[4].toInt().toLong() * a[6].toInt().toLong() +
                    a[2].toInt().toLong() * a[8].toInt().toLong() +
               2 * (a[3].toInt().toLong() * a[7].toInt().toLong() +
                    a[1].toInt().toLong() * a[9].toInt().toLong()))
  out[11] = 2 * (a[5].toInt().toLong() * a[6].toInt().toLong() +
                    a[4].toInt().toLong() * a[7].toInt().toLong() +
                    a[3].toInt().toLong() * a[8].toInt().toLong() +
                    a[2].toInt().toLong() * a[9].toInt().toLong())
  out[12] =      a[6].toInt().toLong() * a[6].toInt().toLong() +
               2 * (a[4].toInt().toLong() * a[8].toInt().toLong() +
               2 * (a[5].toInt().toLong() * a[7].toInt().toLong() +
                    a[3].toInt().toLong() * a[9].toInt().toLong()))
  out[13] = 2 * (a[6].toInt().toLong() * a[7].toInt().toLong() +
                    a[5].toInt().toLong() * a[8].toInt().toLong() +
                    a[4].toInt().toLong() * a[9].toInt().toLong())
  out[14] = 2 * (a[7].toInt().toLong() * a[7].toInt().toLong() +
                    a[6].toInt().toLong() * a[8].toInt().toLong() +
               2 *  a[5].toInt().toLong() * a[9].toInt().toLong())
  out[15] = 2 * (a[7].toInt().toLong() * a[8].toInt().toLong() +
                    a[6].toInt().toLong() * a[9].toInt().toLong())
  out[16] =      a[8].toInt().toLong() * a[8].toInt().toLong() +
               4 *  a[7].toInt().toLong() * a[9].toInt().toLong()
  out[17] = 2 *  a[8].toInt().toLong() * a[9].toInt().toLong()
  out[18] = 2 *  a[9].toInt().toLong() * a[9].toInt().toLong()
    }
}
