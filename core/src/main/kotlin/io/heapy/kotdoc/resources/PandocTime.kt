// PandocMonad getTimestamp/getPOSIXTime and GHC Read Integer; original notices in licenses/.
package io.heapy.kotdoc.resources

import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.parsing.ParserCharacters
import java.math.BigInteger

private val picosPerSecond=BigInteger.TEN.pow(12)
private val picosPerDay=picosPerSecond*BigInteger.valueOf(86400)

/** UTCTime's raw ModifiedJulianDay and DiffTime picoseconds, including leap seconds. */
data class PandocUtcTime(val day: BigInteger,val dayPicoseconds: BigInteger) {
    fun posixPicoseconds() = (day-BigInteger.valueOf(40587))*picosPerDay + dayPicoseconds.min(picosPerDay)
    companion object {
        fun fromPosixPicoseconds(picos: BigInteger): PandocUtcTime {
            var (d,r)=picos.divideAndRemainder(picosPerDay)
            if(r.signum()<0) { d-=BigInteger.ONE;r+=picosPerDay }
            return PandocUtcTime(d+BigInteger.valueOf(40587),r)
        }
    }
}
fun floorPicoseconds(picos: BigInteger): BigInteger {
    val (n,r)=picos.divideAndRemainder(picosPerSecond)
    return if(r.signum()<0) n-BigInteger.ONE else n
}

/** The Integer specialization of Shared.safeRead, including GHC's numeric lexer. */
fun safeReadInteger(input: String): BigInteger? {
    val ps=TextChars.fromString(input).codePoints().toArray();var at=0;var parentheses=0
    fun spaces() { while(at<ps.size && ParserCharacters.isSpace(ps[at])) at++ }
    spaces()
    while(at<ps.size && ps[at]==40) { parentheses++;at++;spaces() }
    val negative=at<ps.size && ps[at]==45
    if(negative) { at++;spaces() }
    if(at>=ps.size || ps[at] !in 48..57) return null
    var base=10
    if(ps[at]==48 && at+2<ps.size) {
        val candidate=when(ps[at+1]) { 98,66->2;111,79->8;120,88->16;else->10 }
        if(candidate!=10 && digit(ps[at+2]) in 0 until candidate) { base=candidate;at+=2 }
    }
    val start=at
    while(at<ps.size && digit(ps[at]) in 0 until base) at++
    if(at==start) return null
    val value=BigInteger(String(ps,start,at-start),base)
    repeat(parentheses) { spaces();if(at>=ps.size || ps[at]!=41) return null;at++ }
    spaces()
    if(at!=ps.size) return null
    return if(negative) value.negate() else value
}
private fun digit(cp: Int) = when(cp) { in 48..57->cp-48;in 65..70->cp-65+10;in 97..102->cp-97+10;else->-1 }
