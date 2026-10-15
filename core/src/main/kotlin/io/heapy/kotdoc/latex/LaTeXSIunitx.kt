// Text.Pandoc.Readers.LaTeX.SIunitx, Pandoc 3.12.1, John MacFarlane.
// GPL-2.0-or-later; licenses/pandoc-GPL-2.0.md.
package io.heapy.kotdoc.latex

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.parsing.*

class LaTeXSIunitx(val kernel: LaTeXKernel = LaTeXKernel()) {
    private fun <A> pure(x: A): LP<A> = ReaderTokenParsec.pure(x)
    private fun <A> defer(p: () -> LP<A>): LP<A> = ReaderTokenParsec.defer(p)
    private fun unit(tok: LP<Inlines>): LP<Inlines> = kernel.keyvals().option(emptyList()).flatMap { options ->
        val p=siUnit(options,tok)
        kernel.grouped(p,Inlines(),Inlines::plus) or p
    }
    private fun num(): LP<Inlines> = kernel.skipopts().then(kernel.braced()).map { tonum(LaTeXTokens.untokenize(it)) }
    private fun valuePrefix(tok: LP<Inlines>) = kernel.bracketed(tok,Inlines(),Inlines::plus).option(Inlines())
    private fun quantity(tok: LP<Inlines>): LP<Inlines> = kernel.skipopts().then(num()).flatMap { value ->
        valuePrefix(tok).flatMap { prefix -> unit(tok).map { u -> concat(listOf(prefix,emptyOr160(prefix),value,emptyOr160(u),u)) } }
    }
    private fun numberList(): LP<Inlines> = kernel.skipopts().then(kernel.braced()).map { ts ->
        listText(LaTeXTokens.untokenize(ts).split(';').map(::tonum))
    }
    private fun quantityList(tok: LP<Inlines>): LP<Inlines> = kernel.keyvals().option(emptyList()).flatMap { options ->
        kernel.braced().flatMap { ts ->
            val nums=LaTeXTokens.untokenize(ts).split(';').map(::tonum)
            val p=siUnit(options,tok)
            (kernel.grouped(p,Inlines(),Inlines::plus) or p).map { u -> listText(nums.map { it+(Builder.str("\u00a0")+u) }) }
        }
    }
    private fun range(includeUnits: Boolean,tok: LP<Inlines>): LP<Inlines> = kernel.skipopts().then(num()).flatMap { start ->
        valuePrefix(tok).flatMap { startPrefix -> num().flatMap { stop -> valuePrefix(tok).flatMap { stopPrefix ->
            (if(includeUnits)unit(tok) else pure(Inlines())).map { u -> concat(listOf(startPrefix,emptyOr160(startPrefix),start,emptyOr160(u),u,
                Builder.text("\u2013"),stopPrefix,emptyOr160(stopPrefix),stop,emptyOr160(u),u)) }
        } } }
    }
    private fun angle(): LP<Inlines> = kernel.skipopts().then(kernel.braced()).map { ts ->
        val parts=LaTeXTokens.untokenize(ts).split(';')
        concat((0..<3).map { i ->
            val text=parts.getOrNull(i) ?: ""
            if(text.isEmpty())Inlines() else Builder.str(text.removePrefix("+"))+Builder.str(listOf("\u00b0","\u2032","\u2033")[i])
        })
    }
    fun siunitxCommands(tok: LP<Inlines>): Map<String,LP<Inlines>> {
        val result=sortedMapOf<String,LP<Inlines>>(UnicodeTextOrder)
        for(name in listOf("si","unit"))result[name]=defer { unit(tok) }
        for(name in listOf("SI","qty"))result[name]=defer { quantity(tok) }
        for(name in listOf("SIrange","qtyrange"))result[name]=defer { range(true,tok) }
        for(name in listOf("SIlist","qtylist"))result[name]=defer { quantityList(tok) }
        result["numrange"]=defer { range(false,tok) };result["numlist"]=defer { numberList() }
        result["num"]=defer { num() };result["ang"]=defer { angle() }
        return java.util.Collections.unmodifiableMap(result)
    }
    fun siUnit(options: List<Pair<String,String>>,tok: LP<Inlines>): LP<Inlines> {
        fun base(): LP<Inlines> = defer {
            kernel.anyControlSeq().flatMap { token ->
                val name=(token.type as io.heapy.kotdoc.tex.TokType.CtrlSeq).text
                val modifier=siUnitModifierMap[name]
                if(modifier!=null)defer { base() }.map { modifier+it }
                else siUnitMap[name]?.let(::pure) ?: ReaderTokenParsec.fail("not a unit command")
            }.attempt() or kernel.satisfyTok(LaTeXKernel::isWordTok).map { Builder.str(it.text) }
        }
        fun suffix(name: String,power: String): LP<(Inlines)->Inlines> = kernel.controlSeq(name).then(kernel.skipopts()).map { { x: Inlines -> x+Builder.superscript(Builder.text(power)) } }
        fun parsedPower(name: String): LP<Inlines> = kernel.controlSeq(name).then(kernel.skipopts()).then(tok).map(::walkHyphenToMinus)
        fun prefix(): LP<(Inlines)->Inlines> = suffix("square","2") or suffix("cubic","3") or
            parsedPower("raisetothe").map { n -> { x: Inlines -> x+Builder.superscript(n) } }
        fun suffix(): LP<(Inlines)->Inlines> = suffix("squared","2") or suffix("cubed","3") or
            parsedPower("tothe").map { n -> { x: Inlines -> x+Builder.superscript(n) } } or
            kernel.symbol('^').then(tok).map(::walkHyphenToMinus).map { n -> { x: Inlines -> x+Builder.superscript(n) } } or
            kernel.symbol('_').then(tok).map(::walkHyphenToMinus).map { n -> { x: Inlines -> x+Builder.subscript(n) } }
        fun part(): LP<Inlines> = defer {
            (kernel.symbol('.').map { Unit } or kernel.symbol('~').map { Unit } or kernel.spaces1()).skipMany().then(
                prefix().flatMap { f -> base().map(f) } or (base() or tok).flatMap { u -> suffix().map { it(u) }.option(u) }
            ).flatMap { u ->
                val infix=(kernel.controlSeq("per").then(defer { part() }).map { next ->
                    if(options.firstOrNull { it.first=="per-mode" }?.second=="symbol")u+Builder.str("/")+next
                    else u+Builder.str("\u00a0")+negateExponent(next)
                } or kernel.symbol('/').then(defer { part() }).map { u+Builder.str("/")+it }).attempt()
                infix.option(u)
            }.attempt()
        }
        return defer { part().many1().map { join(it,Builder.str("\u00a0")) } }
    }

    companion object {
        const val minus="\u2212"
        private fun concat(xs: List<Inlines>) = xs.foldRight(Inlines()) { x,tail -> x+tail }
        private fun join(xs: List<Inlines>,separator: Inlines): Inlines = concat(buildList {
            xs.forEachIndexed { i,x -> if(i>0)add(separator);add(x) }
        })
        private fun listText(xs: List<Inlines>): Inlines = when(xs.size) {
            0->Inlines();1->xs[0]
            else->join(xs.dropLast(1),Builder.str(",")+Builder.space)+Builder.text(", & ")+xs.last()
        }
        fun emptyOr160(value: Inlines) = if(value.values.isEmpty())value else Builder.str("\u00a0")
        fun hyphenToMinus(value: Inline): Inline = if(value is Inline.Str)Inline.Str(value.text.replace("-",minus)) else value
        private val hyphenWalker=object : PandocVisitor() { override fun transformInline(value: Inline) = hyphenToMinus(value) }
        fun walkHyphenToMinus(value: Inlines) = Inlines(hyphenWalker.walkInlines(value.values))
        fun negateExponent(value: Inlines): Inlines {
            val last=value.values.lastOrNull()
            return if(last is Inline.Superscript)Inlines(value.values.dropLast(1))+Builder.superscript(Builder.str(minus)+Inlines(last.content))
                else value+Builder.superscript(Builder.str(minus+"1"))
        }
        private fun char(c: Char): Parsec<Unit,Int> = SourceParsers.char(c.code)
        private fun decimal(): Parsec<Unit,Inlines> {
            val sign=char('+').map { "" } or char('-').map { minus }
            val digits=SourceParsers.satisfy<Unit> { ParserCharacters.isDigit(it) || it=='.'.code }.many1().map { String(it.toIntArray(),0,it.size) }
            val parens=char('(').then(digits).before(char(')'))
            return sign.option("").flatMap { pref -> digits.flatMap { raw ->
                val base=pref+(if(raw.startsWith('.'))"0$raw" else raw)
                parens.option("").map { uncertainty ->
                    if(uncertainty.isEmpty())Builder.str(base) else {
                        val dot=base.indexOf('.')
                        val x=if(dot<0)-1 else base.length-dot-1
                        val y=uncertainty.length
                        val adjusted=if(x==0)uncertainty else if(x>y)"0."+"0".repeat(x-y)+uncertainty.trimEnd('0') else {
                            val rest=uncertainty.drop(y-x).trimEnd('0')
                            uncertainty.take(y-x)+(if(rest.isEmpty())"" else ".$rest")
                        }
                        Builder.str(base+"\u00a0\u00b1\u00a0"+adjusted)
                    }
                }
            } }.attempt()
        }
        private fun numberPart(): Parsec<Unit,Inlines> = decimal() or char(',').map { Builder.str(".") } or
            SourceParsers.string<Unit>("+-").attempt().map { Builder.str("\u00a0\u00b1\u00a0") } or
            SourceParsers.string<Unit>("\\pm").attempt().map { Builder.str("\u00a0\u00b1\u00a0") } or
            char('i').map { Builder.str("i") } or
            char('e').then(decimal()).map { Builder.str("\u00a0\u00d7\u00a0"+"10")+Builder.superscript(it) } or
            char('x').map { Builder.str("\u00a0\u00d7\u00a0") } or char(' ').many1().map { Inlines() }
        private val numberParser: Parsec<Unit,Inlines> by lazy { numberPart().many().map(::concat).before(Parsec.eof()) }
        fun parseNum(): Parsec<Unit,Inlines> = numberParser
        fun tonum(value: String): Inlines {
            val parsed=parseNum().run(ParseState(ParseInput(value),SourcePos(""),Unit))
            return if(parsed.success)parsed.value!! else Builder.text(value)
        }
        val siUnitModifierMap: Map<String,Inlines> = java.util.Collections.unmodifiableMap(sortedMapOf<String,Inlines>(UnicodeTextOrder).apply {
            put("atto",Builder.str("a"))
            put("centi",Builder.str("c"))
            put("deca",Builder.str("d"))
            put("deci",Builder.str("d"))
            put("deka",Builder.str("d"))
            put("exa",Builder.str("E"))
            put("femto",Builder.str("f"))
            put("giga",Builder.str("G"))
            put("hecto",Builder.str("h"))
            put("kilo",Builder.str("k"))
            put("mega",Builder.str("M"))
            put("micro",Builder.str("μ"))
            put("milli",Builder.str("m"))
            put("nano",Builder.str("n"))
            put("peta",Builder.str("P"))
            put("pico",Builder.str("p"))
            put("tera",Builder.str("T"))
            put("yocto",Builder.str("y"))
            put("yotta",Builder.str("Y"))
            put("zepto",Builder.str("z"))
            put("zetta",Builder.str("Z"))
        })
        val siUnitMap: Map<String,Inlines> = java.util.Collections.unmodifiableMap(sortedMapOf<String,Inlines>(UnicodeTextOrder).apply {
            put("fg",Builder.str("fg"))
            put("pg",Builder.str("pg"))
            put("ng",Builder.str("ng"))
            put("ug",Builder.str("μg"))
            put("mg",Builder.str("mg"))
            put("g",Builder.str("g"))
            put("kg",Builder.str("kg"))
            put("amu",Builder.str("u"))
            put("pm",Builder.str("pm"))
            put("nm",Builder.str("nm"))
            put("um",Builder.str("μm"))
            put("mm",Builder.str("mm"))
            put("cm",Builder.str("cm"))
            put("dm",Builder.str("dm"))
            put("m",Builder.str("m"))
            put("km",Builder.str("km"))
            put("as",Builder.str("as"))
            put("fs",Builder.str("fs"))
            put("ps",Builder.str("ps"))
            put("ns",Builder.str("ns"))
            put("us",Builder.str("μs"))
            put("ms",Builder.str("ms"))
            put("s",Builder.str("s"))
            put("fmol",Builder.str("fmol"))
            put("pmol",Builder.str("pmol"))
            put("nmol",Builder.str("nmol"))
            put("umol",Builder.str("μmol"))
            put("mmol",Builder.str("mmol"))
            put("mol",Builder.str("mol"))
            put("kmol",Builder.str("kmol"))
            put("pA",Builder.str("pA"))
            put("nA",Builder.str("nA"))
            put("uA",Builder.str("μA"))
            put("mA",Builder.str("mA"))
            put("A",Builder.str("A"))
            put("kA",Builder.str("kA"))
            put("ul",Builder.str("μl"))
            put("ml",Builder.str("ml"))
            put("l",Builder.str("l"))
            put("hl",Builder.str("hl"))
            put("uL",Builder.str("μL"))
            put("mL",Builder.str("mL"))
            put("L",Builder.str("L"))
            put("hL",Builder.str("hL"))
            put("mHz",Builder.str("mHz"))
            put("Hz",Builder.str("Hz"))
            put("kHz",Builder.str("kHz"))
            put("MHz",Builder.str("MHz"))
            put("GHz",Builder.str("GHz"))
            put("THz",Builder.str("THz"))
            put("mN",Builder.str("mN"))
            put("N",Builder.str("N"))
            put("kN",Builder.str("kN"))
            put("MN",Builder.str("MN"))
            put("Pa",Builder.str("Pa"))
            put("kPa",Builder.str("kPa"))
            put("MPa",Builder.str("MPa"))
            put("GPa",Builder.str("GPa"))
            put("mohm",Builder.str("mΩ"))
            put("kohm",Builder.str("kΩ"))
            put("Mohm",Builder.str("MΩ"))
            put("pV",Builder.str("pV"))
            put("nV",Builder.str("nV"))
            put("uV",Builder.str("μV"))
            put("mV",Builder.str("mV"))
            put("V",Builder.str("V"))
            put("kV",Builder.str("kV"))
            put("W",Builder.str("W"))
            put("uW",Builder.str("μW"))
            put("mW",Builder.str("mW"))
            put("kW",Builder.str("kW"))
            put("MW",Builder.str("MW"))
            put("GW",Builder.str("GW"))
            put("J",Builder.str("J"))
            put("uJ",Builder.str("μJ"))
            put("mJ",Builder.str("mJ"))
            put("kJ",Builder.str("kJ"))
            put("eV",Builder.str("eV"))
            put("meV",Builder.str("meV"))
            put("keV",Builder.str("keV"))
            put("MeV",Builder.str("MeV"))
            put("GeV",Builder.str("GeV"))
            put("TeV",Builder.str("TeV"))
            put("kWh",Builder.str("kWh"))
            put("F",Builder.str("F"))
            put("fF",Builder.str("fF"))
            put("pF",Builder.str("pF"))
            put("K",Builder.str("K"))
            put("dB",Builder.str("dB"))
            put("ampere",Builder.str("A"))
            put("angstrom",Builder.str("Å"))
            put("arcmin",Builder.str("′"))
            put("arcminute",Builder.str("′"))
            put("arcsecond",Builder.str("″"))
            put("astronomicalunit",Builder.str("au"))
            put("atomicmassunit",Builder.str("u"))
            put("bar",Builder.str("bar"))
            put("barn",Builder.str("b"))
            put("becquerel",Builder.str("Bq"))
            put("bel",Builder.str("B"))
            put("bohr",Builder.emph(Builder.str("a"))+Builder.subscript(Builder.str("0")))
            put("candela",Builder.str("cd"))
            put("celsius",Builder.str("°C"))
            put("clight",Builder.emph(Builder.str("c"))+Builder.subscript(Builder.str("0")))
            put("coulomb",Builder.str("C"))
            put("dalton",Builder.str("Da"))
            put("day",Builder.str("d"))
            put("decibel",Builder.str("db"))
            put("degreeCelsius",Builder.str("°C"))
            put("degree",Builder.str("°"))
            put("electronmass",Builder.emph(Builder.str("m"))+Builder.subscript(Builder.str("e")))
            put("electronvolt",Builder.str("eV"))
            put("elementarycharge",Builder.emph(Builder.str("e")))
            put("farad",Builder.str("F"))
            put("gram",Builder.str("g"))
            put("gray",Builder.str("Gy"))
            put("hartree",Builder.emph(Builder.str("E"))+Builder.subscript(Builder.str("h")))
            put("hectare",Builder.str("ha"))
            put("henry",Builder.str("H"))
            put("hertz",Builder.str("Hz"))
            put("hour",Builder.str("h"))
            put("joule",Builder.str("J"))
            put("katal",Builder.str("kat"))
            put("kelvin",Builder.str("K"))
            put("kilogram",Builder.str("kg"))
            put("knot",Builder.str("kn"))
            put("liter",Builder.str("L"))
            put("litre",Builder.str("l"))
            put("lumen",Builder.str("lm"))
            put("lux",Builder.str("lx"))
            put("meter",Builder.str("m"))
            put("metre",Builder.str("m"))
            put("minute",Builder.str("min"))
            put("mmHg",Builder.str("mmHg"))
            put("mole",Builder.str("mol"))
            put("nauticalmile",Builder.str("M"))
            put("neper",Builder.str("Np"))
            put("newton",Builder.str("N"))
            put("ohm",Builder.str("Ω"))
            put("Pa",Builder.str("Pa"))
            put("pascal",Builder.str("Pa"))
            put("percent",Builder.str("%"))
            put("planckbar",Builder.emph(Builder.str("ℏ")))
            put("radian",Builder.str("rad"))
            put("second",Builder.str("s"))
            put("siemens",Builder.str("S"))
            put("sievert",Builder.str("Sv"))
            put("steradian",Builder.str("sr"))
            put("tesla",Builder.str("T"))
            put("tonne",Builder.str("t"))
            put("volt",Builder.str("V"))
            put("watt",Builder.str("W"))
            put("weber",Builder.str("Wb"))
        })
    }
}
