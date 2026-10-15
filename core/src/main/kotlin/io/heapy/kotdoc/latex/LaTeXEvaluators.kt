// Text.Pandoc.Readers.LaTeX.Parsing, © 2006-2024 John MacFarlane.
// GPL-2.0-or-later; Numeric formatting uses retained GHC 9.12.2 routines.
// GHC.Internal.Float/Real, (c) The University of Glasgow 1994-2002;
// BSD-3-Clause, licenses/GHC-base-BSD-3-Clause.txt.
package io.heapy.kotdoc.latex

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Platform
import io.heapy.kotdoc.formats.HaskellDouble
import io.heapy.kotdoc.formats.HaskellNumber
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.parsing.ParserCharacters
import java.math.BigInteger
import kotlin.math.abs

/** GHC's Double transcendental primops call the host C library. */
internal interface LaTeXLibm : Library {
    fun pow(x: Double,y: Double): Double
    fun sqrt(x: Double): Double
    fun exp(x: Double): Double
    fun log(x: Double): Double
    fun sin(x: Double): Double
    fun cos(x: Double): Double
    fun tan(x: Double): Double
    fun asin(x: Double): Double
    fun acos(x: Double): Double
    fun atan(x: Double): Double
}
internal object LaTeXMath {
    val lib: LaTeXLibm by lazy { Native.load(when { Platform.isMac()->"System";Platform.isWindows()->"ucrtbase";else->"m" },LaTeXLibm::class.java) }
}

object LaTeXEvaluators {
    private enum class Rule { Expr, Term, Pow, Factor }
    private class Request(val rule: Rule,val at: Int)
    private class Parsed<N>(val number: N,val at: Int)
    private fun ws(c: Char) = c==' ' || c=='\t' || c=='\n' || c=='\r'
    private class Arithmetic<N>(val floating: Boolean,val number: (String)->N?,val add:(N,N)->N,val sub:(N,N)->N,
        val mul:(N,N)->N,val div:(N,N)->N?,val negate:(N)->N,val pow:(N,N)->N?,val constant:(String)->N?,val fn:(String,List<N>)->N?)
    private fun <N> parse(s: String, arithmetic: Arithmetic<N>): N? {
        fun skip(start: Int): Int { var at=start;while(at<s.length && ws(s[at]))at++;return at }
        fun digit(c: Char)=c in '0'..'9'
        val parser=DeepRecursiveFunction<Request,Parsed<N>?> { req ->
            val at=skip(req.at)
            when(req.rule) {
                Rule.Expr,Rule.Term->{
                    val next=if(req.rule==Rule.Expr)Rule.Term else Rule.Pow
                    val initial=callRecursive(Request(next,at)) ?: return@DeepRecursiveFunction null
                    var value=initial.number;var end=initial.at
                    while(true) {
                        val opAt=skip(end);val op=s.getOrNull(opAt)
                        if(if(req.rule==Rule.Expr)op!='+' && op!='-' else op!='*' && op!='/')break
                        val rhs=callRecursive(Request(next,opAt+1)) ?: return@DeepRecursiveFunction null
                        value=when(op) { '+'->arithmetic.add(value,rhs.number);'-'->arithmetic.sub(value,rhs.number)
                            '*'->arithmetic.mul(value,rhs.number);else->arithmetic.div(value,rhs.number) ?: return@DeepRecursiveFunction null }
                        end=rhs.at
                    }
                    Parsed(value,end)
                }
                Rule.Pow->{
                    val first=callRecursive(Request(Rule.Factor,at)) ?: return@DeepRecursiveFunction null
                    val opAt=skip(first.at)
                    if(arithmetic.floating && s.getOrNull(opAt)=='^') {
                        val rhs=callRecursive(Request(Rule.Pow,opAt+1)) ?: return@DeepRecursiveFunction null
                        Parsed(arithmetic.pow(first.number,rhs.number) ?: return@DeepRecursiveFunction null,rhs.at)
                    } else first
                }
                Rule.Factor->when(val c=s.getOrNull(at)) {
                    null->null
                    '-','+'->{val x=callRecursive(Request(Rule.Factor,at+1)) ?: return@DeepRecursiveFunction null
                        Parsed(if(c=='-')arithmetic.negate(x.number) else x.number,x.at)}
                    '('->{val x=callRecursive(Request(Rule.Expr,at+1)) ?: return@DeepRecursiveFunction null
                        val end=skip(x.at);if(s.getOrNull(end)==')')Parsed(x.number,end+1) else null}
                    else->{
                        if(arithmetic.floating && ParserCharacters.isAlpha(s.codePointAt(at))) {
                            var end=at
                            while(end<s.length && ParserCharacters.isAlpha(s.codePointAt(end)))end+=Character.charCount(s.codePointAt(end))
                            val name=s.substring(at,end);val constant=arithmetic.constant(name)
                            if(constant!=null)Parsed(constant,end) else {
                                val args=ArrayList<N>();var after=skip(end)
                                if(s.getOrNull(after)=='(') {
                                    after++
                                    while(true) {
                                        val x=callRecursive(Request(Rule.Expr,after)) ?: return@DeepRecursiveFunction null
                                        args.add(x.number);after=skip(x.at)
                                        when(s.getOrNull(after)) { ','->after++;')'->{after++;break};else->return@DeepRecursiveFunction null }
                                    }
                                } else {
                                    val x=callRecursive(Request(Rule.Factor,end)) ?: return@DeepRecursiveFunction null
                                    args.add(x.number);after=x.at
                                }
                                arithmetic.fn(name,args)?.let { Parsed(it,after) }
                            }
                        } else if(digit(c) || arithmetic.floating && c=='.') {
                            var end=at
                            while(end<s.length && (digit(s[end]) || arithmetic.floating && s[end]=='.'))end++
                            var literal=s.substring(at,end)
                            if(arithmetic.floating) {
                                if(literal.startsWith('.'))literal="0$literal"
                                if(literal.endsWith('.'))literal+="0"
                                if(s.getOrNull(end)=='e' || s.getOrNull(end)=='E') {
                                    var digitsAt=end+1
                                    if(s.getOrNull(digitsAt)=='+' || s.getOrNull(digitsAt)=='-')digitsAt++
                                    var after=digitsAt
                                    while(after<s.length && digit(s[after]))after++
                                    if(after>digitsAt) { literal+="e"+s.substring(end+1,after);end=after }
                                }
                            }
                            arithmetic.number(literal)?.let { Parsed(it,end) }
                        } else null
                    }
                }
            }
        }
        val parsed=parser(Request(Rule.Expr,0)) ?: return null
        return parsed.number.takeIf { skip(parsed.at)==s.length }
    }
    fun evalInteval(text: String): String? = parse(TextChars.fromString(text),Arithmetic(false,HaskellNumber::readInteger,
        BigInteger::add,BigInteger::subtract,BigInteger::multiply,{a,b->
            if(b==BigInteger.ZERO)null else {val(q,r)=a.divideAndRemainder(b)
                if(r.abs()*BigInteger.TWO>=b.abs())q+BigInteger.valueOf((a.signum()*b.signum()).toLong()) else q}
        },BigInteger::negate,{_,_->null},{null},{_,_->null}))?.toString()
    fun evalFpeval(text: String): String? {
        val x=parse(TextChars.fromString(text).replace("**","^"),Arithmetic(true,HaskellNumber::readDouble,
            Double::plus,Double::minus,Double::times,{a,b->a/b},{-it},{a,b->LaTeXMath.lib.pow(a,b)},
            {when(it){"pi"->Math.PI;"deg"->Math.PI/180;else->null}},::function)) ?: return null
        return if(x.isFinite())formatFp(x) else null
    }
    fun formatFp(value: Double): String {
        val x=HaskellNumber.readDouble(HaskellDouble.showExponent(value,15))!!
        val rounded=HaskellNumber.round(x)
        if(x==rounded.toDouble() && abs(x)<1e16)return rounded.toString()
        val s=HaskellDouble.showFixed(x)
        return if('.' in s)s.trimEnd('0').removeSuffix(".") else s
    }
    private fun rounder(name: String,args: List<Double>): Double? {
        fun round(x: Double): Double=when(name) {
            "round"->HaskellNumber.round(x).toDouble();"floor"->HaskellNumber.floor(x).toDouble()
            "ceil"->HaskellNumber.floor(-x).negate().toDouble()
            else->(if(x<0)HaskellNumber.floor(-x).negate() else HaskellNumber.floor(x)).toDouble()
        }
        if(args.size==1)return round(args[0])
        if(args.size!=2 || args[1]!=HaskellNumber.round(args[1]).toDouble())return null
        val exponent=HaskellNumber.round(args[1])
        // (^^) uses exponentiation by squaring, including negative exponents.
        var n=exponent.abs();var base=10.0;var scale=1.0
        while(n.signum()>0) {if(n.testBit(0))scale*=base;n=n.shiftRight(1);if(n.signum()>0)base*=base}
        if(exponent.signum()<0)scale=1.0/scale
        val y=HaskellNumber.readDouble(HaskellDouble.showExponent(args[0]*scale,15))!!
        return round(y)/scale
    }
    private fun function(name: String,args: List<Double>): Double? {
        fun rad(x: Double)=x*Math.PI/180
        fun degrees(x: Double)=x*180/Math.PI
        // RealFloat's default atan2 uses atan(y/x), not the C atan2 primop.
        fun atan2(y: Double,x: Double): Double {
            fun negativeZero(v: Double)=v==0.0 && v.toRawBits()<0
            if(x>0)return LaTeXMath.lib.atan(y/x)
            if(x==0.0 && y>0)return Math.PI/2
            if(x<0 && y>0)return Math.PI+LaTeXMath.lib.atan(y/x)
            if(x<=0 && y<0 || x<0 && negativeZero(y) || negativeZero(x) && negativeZero(y))return -atan2(-y,x)
            if(y==0.0 && (x<0 || negativeZero(x)))return Math.PI
            if(x==0.0 && y==0.0)return y
            return x+y
        }
        if(args.size==1) {
            val x=args[0];val m=LaTeXMath.lib
            when(name) {
                "abs"->return abs(x);"sign"->return kotlin.math.sign(x);"sqrt"->return m.sqrt(x);"exp"->return m.exp(x);"ln"->return m.log(x)
                "fact"->{if(x<0 || x!=HaskellNumber.round(x).toDouble() || x>=171)return Double.NaN
                    var n=BigInteger.ONE;var i=BigInteger.ONE;val end=HaskellNumber.round(x)
                    while(i<=end){n*=i;i+=BigInteger.ONE};return n.toDouble()}
                "sin"->return m.sin(x);"cos"->return m.cos(x);"tan"->return m.tan(x)
                "cot"->return 1/m.tan(x);"sec"->return 1/m.cos(x);"csc"->return 1/m.sin(x)
                "asin"->return m.asin(x);"acos"->return m.acos(x);"atan"->return m.atan(x)
                "acot"->return m.atan(1/x);"asec"->return m.acos(1/x);"acsc"->return m.asin(1/x)
                "sind"->return m.sin(rad(x));"cosd"->return m.cos(rad(x));"tand"->return m.tan(rad(x))
                "cotd"->return 1/m.tan(rad(x));"secd"->return 1/m.cos(rad(x));"cscd"->return 1/m.sin(rad(x))
                "asind"->return degrees(m.asin(x));"acosd"->return degrees(m.acos(x));"atand"->return degrees(m.atan(x))
                "acotd"->return degrees(m.atan(1/x));"asecd"->return degrees(m.acos(1/x));"acscd"->return degrees(m.asin(1/x))
            }
        }
        return when {
            name=="max" && args.isNotEmpty()->args.reduce {a,b->if(a<=b)b else a}
            name=="min" && args.isNotEmpty()->args.reduce {a,b->if(a<=b)a else b}
            name=="atan" && args.size==2->atan2(args[0],args[1])
            name=="atand" && args.size==2->degrees(atan2(args[0],args[1]))
            name in listOf("round","floor","ceil","trunc")->rounder(name,args)
            else->null
        }
    }
}
