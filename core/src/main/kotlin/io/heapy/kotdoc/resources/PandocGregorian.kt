// time-1.14 Gregorian/OrdinalDate/MonthDay/Private and LocalTime/TimeOfDay.
// Copyright (c) Ashley Yakeley and contributors, 2004-2022;
// Copyright 2004, University Court of the University of Glasgow.
// BSD-2-Clause; full notice in licenses/Time-BSD-2-Clause.txt.
package io.heapy.kotdoc.resources

import java.math.BigInteger

/** The Gregorian day used by LaTeX \today. Integer dates stay unbounded;
 * the intermediate hour/minute and timezone arithmetic follows GHC Int64. */
object PandocGregorian {
    private fun bi(n: Long)=BigInteger.valueOf(n)
    private fun div(n: BigInteger,d: Long): BigInteger {
        val (q,r)=n.divideAndRemainder(bi(d));return if(r.signum()<0)q-BigInteger.ONE else q
    }
    fun showGregorian(day: BigInteger): String {
        val a=day+bi(678575);val quadcent=div(a,146097);val b=a.mod(bi(146097))
        val cent=(b/bi(36524)).min(bi(3));val c=b-cent*bi(36524)
        val quad=c/bi(1461);val d=c.mod(bi(1461));val y=(d/bi(365)).min(bi(3))
        var yd=(d-y*bi(365)+BigInteger.ONE).toInt()
        val year=quadcent*bi(400)+cent*bi(100)+quad*bi(4)+y+BigInteger.ONE
        val leap=year.mod(bi(4))==BigInteger.ZERO && (year.mod(bi(400))==BigInteger.ZERO || year.mod(bi(100))!=BigInteger.ZERO)
        val lengths=intArrayOf(31,if(leap)29 else 28,31,30,31,30,31,31,30,31,30,31)
        var month=1
        while(month<=lengths.size && yd>lengths[month-1]) { yd-=lengths[month-1];month++ }
        val ys=year.abs().toString().padStart(4,'0')
        return (if(year.signum()<0)"-" else "")+ys+"-"+month.toString().padStart(2,'0')+"-"+yd.toString().padStart(2,'0')
    }
    fun zonedDay(time: PandocUtcTime,timezoneMinutes: Long): String {
        val picos=time.dayPicoseconds;val second=BigInteger.TEN.pow(12)
        val h: Long;val m: Long
        if(picos>=bi(86400)*second) { h=23;m=59 } else {
            h=div(picos,3600_000_000_000_000).toLong()
            m=div(picos,60_000_000_000_000).mod(bi(60)).toLong()
        }
        val minutes=m+timezoneMinutes
        val hour=h+Math.floorDiv(minutes,60L)
        return showGregorian(time.day+bi(Math.floorDiv(hour,24L)))
    }
    fun today(runtime: PandocDataRuntime,timezoneMinutes: Long): String = zonedDay(PandocData.getTimestamp(runtime),timezoneMinutes)
}
