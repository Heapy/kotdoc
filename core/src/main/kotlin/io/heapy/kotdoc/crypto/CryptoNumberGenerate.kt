// Crypto.Number.Generate, crypton2.0.1; Vincent Hanquez and contributors.
// Full notice: licenses/crypton-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto
import java.math.BigInteger
/** Arbitrary-precision Integer API; does not replace any specialized word engine. */
object CryptoNumberGenerate {
 enum class GenTopPolicy {SetHighest,SetTwoHighest}
 private val zero=BigInteger.ZERO
 private val one=BigInteger.ONE
 fun generateParams(bits:Int,top:GenTopPolicy?,odd:Boolean,random:(Int)->ByteArray)=generateParamsDeferred(lazyOf(bits),lazyOf(top),lazyOf(odd),random)
 fun generateParamsDeferred(bits:Lazy<Int>,top:Lazy<GenTopPolicy?>,odd:Lazy<Boolean>,random:(Int)->ByteArray):BigInteger {
  val count=bits.value;if(count<=0)return zero
  val size=((count.toLong()+7)/8).toInt();val input=random(size);val copy=input.copyOf()
  try {val bit=(count-1)%8
   fun checkIndex(index:Int){require(index in copy.indices){"random provider storage has no byte at index $index (actual length ${copy.size})"}}
   when(top.value){null->Unit;GenTopPolicy.SetHighest->{checkIndex(0);copy[0]=(copy[0].toInt()or(1 shl bit)).toByte()};GenTopPolicy.SetTwoHighest->if(bit==0){checkIndex(0);copy[0]=1;/* Source bits=1 writes outside its logical copy; retain only its observed numeric prefix. */if(copy.size>1)copy[1]=(copy[1].toInt()or 128).toByte()else if(size!=1)checkIndex(1)}else {checkIndex(0);copy[0]=(copy[0].toInt()or(3 shl(bit-1))).toByte()}}
   checkIndex(0);copy[0]=(copy[0].toInt()and((1 shl(bit+1))-1)).toByte();if(odd.value){checkIndex(size-1);copy[size-1]=(copy[size-1].toInt()or 1).toByte()};return CryptoSerialize.os2ip(copy)
  }finally{copy.fill(0)}
 }
 fun generatePrefix(bits:Int,random:(Int)->ByteArray):BigInteger {if(bits<=0)return zero;val rounded=bits.toLong()+7;val count=(rounded/8).toInt();val offset=(rounded%8).toInt();val input=random(count);return CryptoSerialize.os2ip(input).shiftRight(7-offset)}
 fun generateMax(range:BigInteger,random:(Int)->ByteArray):BigInteger {
  if(range<=one)return zero
  val bits=CryptoNumberBasic.numBits(range)
  if(range<BigInteger.valueOf(127))return generateParams(bits,null,false,random).mod(range)
  val over=bits>3&&!range.testBit(bits-2)&&!range.testBit(bits-3)
  repeat(100){val r=generateParams(bits+if(over)1 else 0,null,false,random);if(r<range)return r;if(over){val r2=r-range;if(r2<range)return r2;val r3=r2-range;if(r3<range)return r3}}
  error("internal: generateMax($range bits=$bits) (${if(over)"over"else"normal"}) doesn't seems to work properly")
 }
 fun generateBetween(low:BigInteger,high:BigInteger,random:(Int)->ByteArray)=low+generateMax(high-low+one,random)
 fun generatePrefixDeferred(bits:Lazy<Int>,random:(Int)->ByteArray)=generatePrefix(bits.value,random)
 fun generateMaxDeferred(range:Lazy<BigInteger>,random:(Int)->ByteArray)=generateMax(range.value,random)
 fun generateBetweenDeferred(low:Lazy<BigInteger>,high:Lazy<BigInteger>,random:(Int)->ByteArray):BigInteger {val upper=high.value;val lower=low.value;return generateBetween(lower,upper,random)}
}
