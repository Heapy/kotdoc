// GHC.Internal.Text.Read.Lex: (c) The University of Glasgow 2002.
// Full original attribution and base BSD notice are retained in licenses/.
package io.heapy.kotdoc.ast

import io.heapy.kotdoc.parsing.ParserCharacters
import io.heapy.kotdoc.parsing.RegexCharacters

/** Structural-token consumer of the complete source lexP scanner. Payloads of
 * Number/String/Char cannot equal the requested structural lexeme, but scanning
 * them (including failed lookahead) remains observable on partial inputs. */
internal object ManyReadLex {
    private const val punctuation=",;()[]{}`"
    private val ascii=listOf("SOH","SO","NUL","STX","ETX","EOT","ENQ","ACK","BEL","BS","HT","LF","VT","FF","CR","SI","DLE","DC1","DC2","DC3","DC4","NAK","SYN","ETB","CAN","EM","SUB","ESC","FS","GS","RS","US","SP","DEL")
    private fun symbol(c:Int)=when(RegexCharacters.category(c)){in 18..21,12,17->c!=39&&c!=34;11->c!=95;else->false}&&c !in punctuation.map(Char::code)
    private fun digit(c:Int?,base:Int):Int? {if(c==null)return null;val n=when(c){in 48..57->c-48;in 65..70->c-65+10;in 97..102->c-97+10;else->return null};return n.takeIf{it<base}}
    fun match(input:ManyReadInput,expected:String):Long? {
        var start=0L;while(input.point(start)?.let(ParserCharacters::isSpace)==true)start++
        val first=input.point(start)?:return null
        var end=start
        fun digits(at:Long,base:Int):Long {var i=at;while(digit(input.point(i),base)!=null)i++;return i}
        fun escape(at:Long):Long? {
            val c=input.point(at)?:return null
            if(c in "abfnrtv\\\"'".map(Char::code))return at+1
            if(c==94){val ctrl=input.point(at+1)?:return null;return (at+2).takeIf{ctrl in 64..95}}
            val base=when(c){111,79->8;120,88->16;else->10};val from=if(base==10)at else at+1
            if(base!=10||digit(c,10)!=null){val stop=digits(from,base);if(stop==from)return null;var n=0L;for(i in from until stop)n=(n*base+digit(input.point(i),base)!!).coerceAtMost(1114112);return stop.takeIf{n<=1114111}}
            var candidates=ascii;var i=at;var accepted:Long?=null
            while(candidates.isNotEmpty()) {
                val complete=candidates.any{it.length.toLong()==i-at};if(complete)accepted=i
                val longer=candidates.filter{it.length.toLong()>i-at};if(longer.isEmpty())return accepted
                val next=input.point(i)?:return accepted
                candidates=longer.filter{it[(i-at).toInt()].code==next};i++
            }
            return accepted
        }
        fun quoted(quote:Int):Long? {
            var i=start+1
            if(quote==39){val c=input.point(i)?:return null
                if(c==92)i=escape(i+1)?:return null else {if(c==39)return null;i++}
                return (i+1).takeIf{input.point(i)==39}
            }
            while(true){val c=input.point(i)?:return null
                if(c==34)return i+1
                if(c!=92){i++;continue}
                val next=input.point(i+1)?:return null
                if(next==38){i+=2;continue}
                if(ParserCharacters.isSpace(next)){i+=2;while(input.point(i)?.let(ParserCharacters::isSpace)==true)i++;if(input.point(i)!=92)return null;i++;continue}
                i=escape(i+1)?:return null
            }
        }
        when {
            first==39||first==34->{quoted(first);return null}
            first in 48..57->{
                if(first==48){val base=when(input.point(start+1)){98,66->2;111,79->8;120,88->16;else->null}
                    if(base!=null){val stop=digits(start+2,base);if(stop>start+2)return null}}
                end=digits(start,10)
                if(input.point(end)==46){val stop=digits(end+1,10);if(stop>end+1)end=stop}
                if(input.point(end)==101||input.point(end)==69){var at=end+1;if(input.point(at)==43||input.point(at)==45)at++;digits(at,10)}
                return null
            }
            first in punctuation.map(Char::code)->end++
            symbol(first)->{end++;while(input.point(end)?.let(::symbol)==true)end++}
            ParserCharacters.isAlpha(first)||first==95->{end++;while(input.point(end)?.let{ParserCharacters.isAlphaNum(it)||it==95||it==39}==true)end++}
            else->return null
        }
        val points=expected.codePoints().toArray()
        if(end-start!=points.size.toLong())return null
        for(i in points.indices)if(input.point(start+i)!=points[i])return null
        return end
    }
}
