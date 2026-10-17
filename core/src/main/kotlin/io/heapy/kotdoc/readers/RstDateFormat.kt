// Data.Time.Format.Format.{Class,Instances}, time 1.14, UTC/defaultTimeLocale closure.
// Copyright (c) Ashley Yakeley and contributors, 2004-2023; University of Glasgow, 2004.
// BSD-2-Clause; full notice in licenses/Time-BSD-2-Clause.txt.
package io.heapy.kotdoc.readers
import io.heapy.kotdoc.parsing.RegexCharacters
/** Formatting used by the reader's timestamp effect interpretation; picoseconds are retained. */
internal object RstDateFormat {
 private val one=java.math.BigInteger.ONE
 private fun bi(n:Long)=java.math.BigInteger.valueOf(n)
 private fun div(n:java.math.BigInteger,d:Long):java.math.BigInteger {val divisor=bi(d);val(q,r)=n.divideAndRemainder(divisor);return if(r.signum()<0)q-one else q}
 private fun mod(n:java.math.BigInteger,d:Long)=n-div(n,d)*bi(d)
 private val picos=java.math.BigInteger.TEN.pow(12)
 private val dayPicos=picos*bi(86400)
 private fun leap(year:java.math.BigInteger)=mod(year,4).signum()==0&&(mod(year,400).signum()==0||mod(year,100).signum()!=0)
 private fun monthLengths(year:java.math.BigInteger)=listOf(31,if(leap(year))29 else 28,31,30,31,30,31,31,30,31,30,31)
 private fun ordinal(day:java.math.BigInteger):Pair<java.math.BigInteger,Int>{
  val a=day+bi(678575);val quadcent=div(a,146097);val b=mod(a,146097).toLong();val cent=minOf(b/36524,3);val c=b-cent*36524;val quad=c/1461;val d=c%1461;val y=minOf(d/365,3)
  return quadcent*bi(400)+bi(cent*100+quad*4+y+1) to(d-y*365+1).toInt()
 }
 fun fromGregorian(year:java.math.BigInteger,month:Int,day:Int):java.math.BigInteger{
  val lengths=monthLengths(year);val m=month.coerceIn(1,12);val yd=lengths.take(m-1).sum()+day.coerceIn(1,lengths[m-1]);val y=year-one
  return bi(yd.toLong())+y*bi(365)+div(y,4)-div(y,100)+div(y,400)-bi(678576)
 }
 data class Calendar(val year:java.math.BigInteger,val monthValue:Int,val dayOfMonth:Int,val dayOfYear:Int,val dow:Int,val isoYear:java.math.BigInteger,val isoWeek:Long,val isoDow:Int)
 data class Timestamp(val utc:io.heapy.kotdoc.resources.PandocUtcTime){
  private val seconds=div(utc.dayPicoseconds,1000000000000)
  private val h=if(utc.dayPicoseconds>=dayPicos)23L else div(div(seconds,60),60).toLong()
  val hour=Math.floorMod(h,24).toInt()
  val minute=if(utc.dayPicoseconds>=dayPicos)59 else mod(div(seconds,60),60).toInt()
  val second=if(utc.dayPicoseconds>=dayPicos)seconds-bi(86400)+bi(60)else mod(seconds,60)
  val picoseconds=mod(utc.dayPicoseconds,1000000000000).toLong()
  // FormatTime UTCTime delegates through UTC ZonedTime: Int64 hour wrapping
  // and day normalization occur before the POSIX conversion.
  val epochSeconds by lazy {
   val normalizedDay=utc.day+bi(Math.floorDiv(h,24))
   val normalizedPicos=(bi(hour.toLong())*bi(3600)+bi(minute.toLong())*bi(60)+second)*picos+bi(picoseconds)
   div((normalizedDay-bi(40587))*dayPicos+normalizedPicos.min(dayPicos),1000000000000)
  }
  val calendar:Calendar by lazy {
   val day=utc.day+bi(Math.floorDiv(h,24));val(year,yd)=ordinal(day);var remaining=yd;val lengths=monthLengths(year);var month=1
   while(month<12&&remaining>lengths[month-1]){remaining-=lengths[month-1];month++}
   val dow=mod(day+bi(2),7).toInt()+1
   fun weekday(day:java.math.BigInteger)=Math.floorMod((day+bi(3)).toLong(),7L).toInt().let{if(it==0)7 else it}
   fun firstWeek(year:java.math.BigInteger):java.math.BigInteger{val jan=fromGregorian(year,1,1)-bi(3);return jan+bi(Math.floorMod(1-weekday(jan),7).toLong())}
   val first=firstWeek(year);val next=firstWeek(year+one)
   val isoYear=if(day<first)year-one else if(day<next)year else year+one
   val beginning=if(day<first)firstWeek(year-one)else if(day<next)first else next
   Calendar(year,month,remaining,yd,dow,isoYear,Math.floorDiv((day-beginning).toLong(),7L)+1,weekday(day))
  }
 }
 val epoch=Timestamp(io.heapy.kotdoc.resources.PandocUtcTime(bi(40587),java.math.BigInteger.ZERO))
 private val months=listOf("January","February","March","April","May","June","July","August","September","October","November","December")
 private val days=listOf("Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday")
 fun format(raw:String,time:Timestamp=epoch):String=buildString{
  var at=0
  while(at<raw.length){if(raw[at]!='%'){append(raw[at++]);continue};val begin=++at;var modifier:Char?=null
   if(at<raw.length&&raw[at]in "_-0^#")modifier=raw[at++]
   val first=at;while(at<raw.length&&raw[at]in '0'..'9')at++
   val width=if(first==at)null else java.math.BigInteger(raw.substring(first,at)).toLong()
   val alt=at<raw.length&&raw[at]=='E';if(alt)at++
   if(at==raw.length){append('%');at=begin;continue}
   val point=raw.codePointAt(at);at+=Character.charCount(point);val spec=if(point<=0xffff)point.toChar()else '\u0000';var value:String
   fun pad(text:String,number:Boolean=false,default:Boolean=false,size:Int=1,char:Char=if(number)'0'else' ',fraction:Boolean=false,widthOverride:Long?=width):String{
    val padding=when(modifier){'-'->null;'_'->' ';'0'->'0';else->char}
    val active=widthOverride!=null||when(modifier){'-'->false;'_','0'->true;else->default}
    val count=if(widthOverride==null)size.toLong()else if(modifier=='-'||fraction)widthOverride else maxOf(widthOverride,size.toLong())
    if(!active)return text
    val c=padding?:' '
    fun repeated(n:Long):String{if(n<=0)return "";if(n>Int.MAX_VALUE)throw OutOfMemoryError("time format width exceeds JVM string capacity");return c.toString().repeat(n.toInt())}
    if(fraction)return if(count<text.length)text.take(count.coerceAtLeast(0).toInt())else text+repeated(count-text.length)
    return if(number&&text.startsWith('-'))"-"+repeated(count-(text.length-1))+text.drop(1)else repeated(count-text.length)+text
   }
   fun number(n:Number,size:Int=2,default:Boolean=true,char:Char='0')=pad(n.toString(),true,default,size,char)
   val d=time.calendar;val dow=d.dow
   value=when(spec){
    '%'->pad("%");'n'->pad("\n");'t'->pad("\t")
    'c'->pad(format("%a %b %e %H:%M:%S %Z %Y",time));'R'->pad(format("%H:%M",time));'T','X'->pad(format("%H:%M:%S",time));'r'->pad(format("%I:%M:%S %p",time));'D','x'->pad(format("%m/%d/%y",time));'F'->pad(format("%Y-%m-%d",time))
    'Y'->number(d.year,4,false);'y'->number(mod(d.year,100));'C'->number(div(d.year,100),2,false)
    'G'->number(d.isoYear,4,false);'g'->number(mod(d.isoYear,100));'f'->number(div(d.isoYear,100),2,false)
    'V'->number(d.isoWeek.toLong());'U'->number(((d.dayOfYear+6-(dow%7))/7).toLong());'W'->number(((d.dayOfYear+6-(dow-1))/7).toLong())
    'm'->number(d.monthValue.toLong());'d'->number(d.dayOfMonth.toLong());'e'->number(d.dayOfMonth.toLong(),2,true,' ');'j'->number(d.dayOfYear.toLong(),3);'u'->number(d.isoDow.toLong(),1);'w'->number((dow%7).toLong(),1)
    'B'->pad(months[d.monthValue-1]);'b','h'->pad(months[d.monthValue-1].take(3));'A'->pad(days[dow-1]);'a'->pad(days[dow-1].take(3));'P'->pad(if(time.hour<12)"am"else"pm");'p'->pad(if(time.hour<12)"AM"else"PM")
    'H'->number(time.hour.toLong());'k'->number(time.hour.toLong(),2,true,' ');'I'->number((Math.floorMod(time.hour-1,12)+1).toLong());'l'->number((Math.floorMod(time.hour-1,12)+1).toLong(),2,true,' ');'M'->number(time.minute.toLong());'S'->number(time.second.toLong());'s'->number(time.epochSeconds,1)
    'q','Q'->{val fraction=pad(time.picoseconds.toString().padStart(12,'0').trimEnd('0'),default=spec=='q',size=12,char='0',fraction=true);if(spec=='Q'&&fraction.isNotEmpty())".$fraction"else fraction}
     'z'->"+"+(if(!alt)pad("0",number=true,default=true,size=4,char='0')else pad("0",number=true,default=true,size=2,char='0',widthOverride=width?.let{maxOf(0L,(if(modifier=='-')it else maxOf(it,5L))-3L)})+":00");'Z'->pad("UTC")
    else->""
   }
   if(modifier=='^'||modifier=='#')value=buildString{value.codePoints().forEach{appendCodePoint(if(modifier=='^')RegexCharacters.toUpper(it)else RegexCharacters.toLower(it))}}
   append(value)
  }
 }
}
