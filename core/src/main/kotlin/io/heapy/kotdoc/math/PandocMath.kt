// Text.Pandoc.Writers.Math, Pandoc 3.12.1; GPL-2.0-or-later.
// Original complete source and notice are retained in upstream/pandoc and licenses/.
package io.heapy.kotdoc.math

import io.heapy.kotdoc.ast.Inline
import io.heapy.kotdoc.ast.MathType
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.options.DEFAULT_KA_TEX_URL
import io.heapy.kotdoc.options.DEFAULT_MATH_JAX_URL
import io.heapy.kotdoc.parsing.Deferred
import io.heapy.kotdoc.state.PandocState

/** Either a fallback Inline, or a shared, call-by-need writer result. */
sealed class MathConversion<out A> {
    class Converted<A> internal constructor(private val result: Deferred<A>): MathConversion<A>() {
        val value: A get()=result.value()
    }
    class Fallback(val inline: Inline): MathConversion<Nothing>()
}

object PandocMath {
    val defaultMathJaxURL: String get()=DEFAULT_MATH_JAX_URL
    val defaultKaTeXURL: String get()=DEFAULT_KA_TEX_URL
    private fun fallback(type: MathType,input: String): Inline {
        val delimiter=if(type==MathType.DisplayMath) "$$" else "$"
        return Inline.Str(delimiter+input+delimiter)
    }
    /** Parsing/logging happens now; a successful writer runs once when value is demanded. */
    fun <A> convertMath(state: PandocState,writer:(DisplayType,List<MathExp>)->A,type: MathType,input: String): MathConversion<A> {
        val text=TextChars.fromString(input)
        return when(val result=MathTeXReader.readTeX(text)) {
            is MathTeXResult.Success->MathConversion.Converted(Deferred.later {
                writer(if(type==MathType.DisplayMath) DisplayType.DisplayBlock else DisplayType.DisplayInline,result.expressions)
            })
            is MathTeXResult.Failure->{state.report(LogMessage.CouldNotConvertTeXMath(text,result.error));MathConversion.Fallback(fallback(type,text))}
        }
    }
    fun texMathToInlines(state: PandocState,type: MathType,input: String): List<Inline> {
        val text=TextChars.fromString(input)
        return when(val result=convertMath(state,MathPandocWriter::writePandoc,type,text)) {
            is MathConversion.Converted->result.value ?: run {
                state.report(LogMessage.CouldNotConvertTeXMath(text,""));ownMathList(listOf(fallback(type,text)))
            }
            is MathConversion.Fallback->ownMathList(listOf(result.inline))
        }
    }
}
