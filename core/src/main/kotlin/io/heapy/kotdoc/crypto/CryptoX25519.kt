// Donna X25519, crypton 2.0.1; Adam Langley / Google / Daniel J. Bernstein.
// Notices: licenses/curve25519-donna-Google-BSD-3-Clause.txt,
// licenses/curve25519-donna-public-domain.txt.
package io.heapy.kotdoc.crypto

internal class CryptoX25519(val bits: Int=64) {
    private val field=CryptoX25519Field(bits)
    private val n=field.limbs
    typealias Region=CryptoP256Words.Region
    private fun buffer()=Region(LongArray(19))
    fun swap(a: Region,b: Region,bit: Long) {
        if(bits==32) {
            val mask=(-bit).toInt()
            for(i in 0..<10) {val x=mask and(a[i].toInt() xor b[i].toInt());a[i]=(a[i].toInt() xor x).toLong();b[i]=(b[i].toInt() xor x).toLong()}
        } else {
            val mask=-bit
            for(i in 0..<5) {val x=mask and(a[i] xor b[i]);a[i]=a[i] xor x;b[i]=b[i] xor x}
        }
    }
    fun monty(x2: Region,z2: Region,x3: Region,z3: Region,x: Region,z: Region,xprime: Region,zprime: Region,qmqp: Region) {
        val origx=buffer();val origxprime=buffer();val zzz=buffer();val xx=buffer();val zz=buffer();val xxprime=buffer();val zzprime=buffer();val zzzprime=buffer()
        origx.copyFrom(x,n);field.sum(x,z);field.difference(z,origx)
        origxprime.copyFrom(xprime,n);field.sum(xprime,zprime);field.difference(zprime,origxprime)
        if(bits==32) {
            field.product(xxprime,xprime,z);field.product(zzprime,x,zprime)
            field.reduceDegree(xxprime);field.reduceCoefficients(xxprime);field.reduceDegree(zzprime);field.reduceCoefficients(zzprime)
        }else {field.multiply(xxprime,xprime,z);field.multiply(zzprime,x,zprime)}
        origxprime.copyFrom(xxprime,n);field.sum(xxprime,zzprime);field.difference(zzprime,origxprime)
        if(bits==32) {
            val xxxprime=buffer();field.square(xxxprime,xxprime);field.square(zzzprime,zzprime)
            field.product(zzprime,zzzprime,qmqp);field.reduceDegree(zzprime);field.reduceCoefficients(zzprime)
            x3.copyFrom(xxxprime,n);z3.copyFrom(zzprime,n)
        }else {field.square(x3,xxprime);field.square(zzzprime,zzprime);field.multiply(z3,zzzprime,qmqp)}
        field.square(xx,x);field.square(zz,z)
        if(bits==32) {field.product(x2,xx,zz);field.reduceDegree(x2);field.reduceCoefficients(x2)}else field.multiply(x2,xx,zz)
        field.difference(zz,xx);field.scalar(zzz,zz,121665)
        if(bits==32)field.reduceCoefficients(zzz)
        field.sum(zzz,xx)
        if(bits==32) {field.product(z2,zz,zzz);field.reduceDegree(z2);field.reduceCoefficients(z2)}else field.multiply(z2,zz,zzz)
    }
    fun multiply(resultx: Region,resultz: Region,scalar: CryptoByteSpan,q: Region,observe: ((Int,List<LongArray>)->Unit)?=null) {
        var nqpqx=buffer();var nqpqz=buffer();var nqx=buffer();var nqz=buffer()
        var nqpqx2=buffer();var nqpqz2=buffer();var nqx2=buffer();var nqz2=buffer()
        nqpqz[0]=1;nqx[0]=1;nqpqz2[0]=1;nqz2[0]=1;nqpqx.copyFrom(q,n)
        for(i in 0..<32) {
            var byte=scalar.data[scalar.offset+31-i].toInt() and 255
            for(j in 0..<8) {
                val bit=(byte ushr 7).toLong()
                swap(nqx,nqpqx,bit);swap(nqz,nqpqz,bit)
                monty(nqx2,nqz2,nqpqx2,nqpqz2,nqx,nqz,nqpqx,nqpqz,q)
                swap(nqx2,nqpqx2,bit);swap(nqz2,nqpqz2,bit)
                var t=nqx;nqx=nqx2;nqx2=t;t=nqz;nqz=nqz2;nqz2=t
                t=nqpqx;nqpqx=nqpqx2;nqpqx2=t;t=nqpqz;nqpqz=nqpqz2;nqpqz2=t
                observe?.invoke(i*8+j,listOf(nqx,nqz,nqpqx,nqpqz).map {r->LongArray(n){r[it]}})
                byte=(byte shl 1) and 255
            }
        }
        resultx.copyFrom(nqx,n);resultz.copyFrom(nqz,n)
    }
    fun reciprocal(out: Region,z: Region,observe: ((String,LongArray)->Unit)?=null) {
        if(bits==32) {
  val z2=buffer()
  val z9=buffer()
  val z11=buffer()
  val z2_5_0=buffer()
  val z2_10_0=buffer()
  val z2_20_0=buffer()
  val z2_50_0=buffer()
  val z2_100_0=buffer()
  val t0=buffer()
  val t1=buffer()
  

   field.square(z2,z);observe?.invoke("square:z2",LongArray(n){z2[it]});
   field.square(t1,z2);observe?.invoke("square:t1",LongArray(n){t1[it]});
   field.square(t0,t1);observe?.invoke("square:t0",LongArray(n){t0[it]});
   field.multiply(z9,t0,z);observe?.invoke("mul:z9",LongArray(n){z9[it]});
   field.multiply(z11,z9,z2);observe?.invoke("mul:z11",LongArray(n){z11[it]});
   field.square(t0,z11);observe?.invoke("square:t0",LongArray(n){t0[it]});
   field.multiply(z2_5_0,t0,z9);observe?.invoke("mul:z2_5_0",LongArray(n){z2_5_0[it]});

   field.square(t0,z2_5_0);observe?.invoke("square:t0",LongArray(n){t0[it]});
   field.square(t1,t0);observe?.invoke("square:t1",LongArray(n){t1[it]});
   field.square(t0,t1);observe?.invoke("square:t0",LongArray(n){t0[it]});
   field.square(t1,t0);observe?.invoke("square:t1",LongArray(n){t1[it]});
   field.square(t0,t1);observe?.invoke("square:t0",LongArray(n){t0[it]});
   field.multiply(z2_10_0,t0,z2_5_0);observe?.invoke("mul:z2_10_0",LongArray(n){z2_10_0[it]});

   field.square(t0,z2_10_0);observe?.invoke("square:t0",LongArray(n){t0[it]});
   field.square(t1,t0);observe?.invoke("square:t1",LongArray(n){t1[it]});
   for(i in 2..<10 step 2) { field.square(t0,t1);observe?.invoke("square:t0",LongArray(n){t0[it]}); field.square(t1,t0);observe?.invoke("square:t1",LongArray(n){t1[it]}); }
   field.multiply(z2_20_0,t1,z2_10_0);observe?.invoke("mul:z2_20_0",LongArray(n){z2_20_0[it]});

   field.square(t0,z2_20_0);observe?.invoke("square:t0",LongArray(n){t0[it]});
   field.square(t1,t0);observe?.invoke("square:t1",LongArray(n){t1[it]});
   for(i in 2..<20 step 2) { field.square(t0,t1);observe?.invoke("square:t0",LongArray(n){t0[it]}); field.square(t1,t0);observe?.invoke("square:t1",LongArray(n){t1[it]}); }
   field.multiply(t0,t1,z2_20_0);observe?.invoke("mul:t0",LongArray(n){t0[it]});

   field.square(t1,t0);observe?.invoke("square:t1",LongArray(n){t1[it]});
   field.square(t0,t1);observe?.invoke("square:t0",LongArray(n){t0[it]});
   for(i in 2..<10 step 2) { field.square(t1,t0);observe?.invoke("square:t1",LongArray(n){t1[it]}); field.square(t0,t1);observe?.invoke("square:t0",LongArray(n){t0[it]}); }
   field.multiply(z2_50_0,t0,z2_10_0);observe?.invoke("mul:z2_50_0",LongArray(n){z2_50_0[it]});

   field.square(t0,z2_50_0);observe?.invoke("square:t0",LongArray(n){t0[it]});
   field.square(t1,t0);observe?.invoke("square:t1",LongArray(n){t1[it]});
   for(i in 2..<50 step 2) { field.square(t0,t1);observe?.invoke("square:t0",LongArray(n){t0[it]}); field.square(t1,t0);observe?.invoke("square:t1",LongArray(n){t1[it]}); }
   field.multiply(z2_100_0,t1,z2_50_0);observe?.invoke("mul:z2_100_0",LongArray(n){z2_100_0[it]});

   field.square(t1,z2_100_0);observe?.invoke("square:t1",LongArray(n){t1[it]});
   field.square(t0,t1);observe?.invoke("square:t0",LongArray(n){t0[it]});
   for(i in 2..<100 step 2) { field.square(t1,t0);observe?.invoke("square:t1",LongArray(n){t1[it]}); field.square(t0,t1);observe?.invoke("square:t0",LongArray(n){t0[it]}); }
   field.multiply(t1,t0,z2_100_0);observe?.invoke("mul:t1",LongArray(n){t1[it]});

   field.square(t0,t1);observe?.invoke("square:t0",LongArray(n){t0[it]});
   field.square(t1,t0);observe?.invoke("square:t1",LongArray(n){t1[it]});
   for(i in 2..<50 step 2) { field.square(t0,t1);observe?.invoke("square:t0",LongArray(n){t0[it]}); field.square(t1,t0);observe?.invoke("square:t1",LongArray(n){t1[it]}); }
   field.multiply(t0,t1,z2_50_0);observe?.invoke("mul:t0",LongArray(n){t0[it]});

   field.square(t1,t0);observe?.invoke("square:t1",LongArray(n){t1[it]});
   field.square(t0,t1);observe?.invoke("square:t0",LongArray(n){t0[it]});
   field.square(t1,t0);observe?.invoke("square:t1",LongArray(n){t1[it]});
   field.square(t0,t1);observe?.invoke("square:t0",LongArray(n){t0[it]});
   field.square(t1,t0);observe?.invoke("square:t1",LongArray(n){t1[it]});
   field.multiply(out,t1,z11);observe?.invoke("mul:out",LongArray(n){out[it]});
        }else {
  val a=buffer();val t0=buffer();val b=buffer();val c=buffer()

   field.square(a,z,1);observe?.invoke("square1:a",LongArray(n){a[it]}); 
   field.square(t0,a,2);observe?.invoke("square2:t0",LongArray(n){t0[it]});
   field.multiply(b,t0,z);observe?.invoke("mul:b",LongArray(n){b[it]}); 
   field.multiply(a,b,a);observe?.invoke("mul:a",LongArray(n){a[it]}); 
   field.square(t0,a,1);observe?.invoke("square1:t0",LongArray(n){t0[it]});
   field.multiply(b,t0,b);observe?.invoke("mul:b",LongArray(n){b[it]});
   field.square(t0,b,5);observe?.invoke("square5:t0",LongArray(n){t0[it]});
   field.multiply(b,t0,b);observe?.invoke("mul:b",LongArray(n){b[it]});
   field.square(t0,b,10);observe?.invoke("square10:t0",LongArray(n){t0[it]});
   field.multiply(c,t0,b);observe?.invoke("mul:c",LongArray(n){c[it]});
   field.square(t0,c,20);observe?.invoke("square20:t0",LongArray(n){t0[it]});
   field.multiply(t0,t0,c);observe?.invoke("mul:t0",LongArray(n){t0[it]});
   field.square(t0,t0,10);observe?.invoke("square10:t0",LongArray(n){t0[it]});
   field.multiply(b,t0,b);observe?.invoke("mul:b",LongArray(n){b[it]});
   field.square(t0,b,50);observe?.invoke("square50:t0",LongArray(n){t0[it]});
   field.multiply(c,t0,b);observe?.invoke("mul:c",LongArray(n){c[it]});
   field.square(t0,c,100);observe?.invoke("square100:t0",LongArray(n){t0[it]});
   field.multiply(t0,t0,c);observe?.invoke("mul:t0",LongArray(n){t0[it]});
   field.square(t0,t0,50);observe?.invoke("square50:t0",LongArray(n){t0[it]});
   field.multiply(t0,t0,b);observe?.invoke("mul:t0",LongArray(n){t0[it]});
   field.square(t0,t0,5);observe?.invoke("square5:t0",LongArray(n){t0[it]});
   field.multiply(out,t0,a);observe?.invoke("mul:out",LongArray(n){out[it]});
        }
    }
    fun donna(out: CryptoByteSpan,secret: CryptoByteSpan,basepoint: CryptoByteSpan,round: ((Int,List<LongArray>)->Unit)?=null,inversion: ((String,LongArray)->Unit)?=null) {
        val e=ByteArray(32){secret.data[secret.offset+it]}
        e[0]=(e[0].toInt() and 248).toByte();e[31]=((e[31].toInt() and 127) or 64).toByte()
        val bp=buffer();val x=buffer();val z=buffer();val inverse=buffer()
        field.expand(bp,basepoint);multiply(x,z,CryptoByteSpan(e),bp,round);reciprocal(inverse,z,inversion);field.multiply(z,x,inverse);field.contract(out,z)
    }
}
