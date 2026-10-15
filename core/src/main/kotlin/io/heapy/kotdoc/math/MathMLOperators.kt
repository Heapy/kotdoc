// Text.TeXMath.Readers.MathML.MMLDict, © 2014 Matthew Pickering, GPL-2.0-or-later.
package io.heapy.kotdoc.math

import io.heapy.kotdoc.layout.TextChars

object MathMLOperators {
    val operators: List<MathOperatorRecord> get()=mathMLOperatorTable
    private val dictionary=operators.associateBy {it.oper to it.form}
    fun getMathMLOperator(text: String,form: FormType): MathOperatorRecord? {
        val s=TextChars.fromString(text)
        return listOf(form,FormType.FInfix,FormType.FPostfix,FormType.FPrefix).firstNotNullOfOrNull {dictionary[s to it]}
    }
}
