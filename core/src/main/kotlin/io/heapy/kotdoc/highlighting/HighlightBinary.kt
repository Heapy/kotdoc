// Skylighting.Types 0.15 / Data.Binary 0.8.9.3.
// Copyright John MacFarlane and binary contributors. BSD-3-Clause;
// licenses/Skylighting-core-BSD-3-Clause.txt, Binary-BSD-3-Clause.txt.
package io.heapy.kotdoc.highlighting

/** Remaining public highlighting model Binary instances. */
@Suppress("UNCHECKED_CAST")
object HighlightBinary {
    private fun record(fields: List<BinaryShape>, split: (Any?) -> List<Any?>, build: (List<Any?>) -> Any?) = BinaryShape.Product(fields,split,build)
    private val byte = BinaryShape.Scalar({ o,v -> o.byte((v as UByte).toInt()) },{ it.byte().toUByte() })
    private val bool = BinaryShape.Scalar({ o,v -> o.byte(if (v as Boolean) 1 else 0) },{
        val b=it.byte(); if (b>1) it.fail("Could not map value $b to Bool"); b==1
    })
    private fun optional(shape: BinaryShape) = BinaryShape.Sum(listOf(record(emptyList(),{ emptyList() },{ null }),record(listOf(shape),{ listOf(it) },{ it[0] })),{ if (it==null) 0 else 1 },strict=false)
    private val colorShape = record(listOf(byte,byte,byte),{ (it as HighlightColor).let { c -> listOf(c.red,c.green,c.blue) } },{ HighlightColor(it[0] as UByte,it[1] as UByte,it[2] as UByte) })
    private val maybeColor = optional(colorShape)
    private val tokenStyleShape = record(listOf(maybeColor,maybeColor,bool,bool,bool),{ (it as TokenStyle).let { t -> listOf(t.color,t.background,t.bold,t.italic,t.underline) } },{ TokenStyle(it[0] as HighlightColor?,it[1] as HighlightColor?,it[2] as Boolean,it[3] as Boolean,it[4] as Boolean) })
    private val entry = record(listOf(SyntaxBinary.tokenType.shape,tokenStyleShape),{ (it as Pair<*,*>).let { e -> listOf(e.first,e.second) } },{ it[0] to it[1] })
    private val tokenMap = BinaryShape.Sequence(entry,{ (it as Map<TokenType,TokenStyle>).entries.map { e -> e.key to e.value } },{ BinaryAscMap(it as List<Pair<TokenType,TokenStyle>>,Comparator.naturalOrder()) })
    private val styleShape = record(listOf(tokenMap,maybeColor,maybeColor,maybeColor,maybeColor),{ (it as HighlightStyle).let { s -> listOf(s.tokenStyles,s.defaultColor,s.backgroundColor,s.lineNumberColor,s.lineNumberBackgroundColor) } },{ HighlightStyle(it[0] as Map<TokenType,TokenStyle>,it[1] as HighlightColor?,it[2] as HighlightColor?,it[3] as HighlightColor?,it[4] as HighlightColor?) })
    private val levelShape = BinaryShape.Scalar({ o,v -> o.byte((v as ANSIColorLevel).ordinal) },{ val b=it.byte(); ANSIColorLevel.entries.getOrNull(b) ?: it.fail("Unknown encoding for constructor") })
    private val texts = BinaryShape.Sequence(SyntaxBinary.text.shape,{ (it as List<String>).toList() },{ syntaxList(it as List<String>) })
    private val optionsShape = record(listOf(bool,SyntaxBinary.int64.shape,bool,bool,texts,texts,SyntaxBinary.text.shape,levelShape),{ (it as FormatOptions).fields },{ FormatOptions(it[0] as Boolean,it[1] as Long,it[2] as Boolean,it[3] as Boolean,it[4] as List<String>,it[5] as List<String>,it[6] as String,it[7] as ANSIColorLevel) })
    val color = SyntaxBinaryCodec<HighlightColor>(colorShape)
    val tokenStyle = SyntaxBinaryCodec<TokenStyle>(tokenStyleShape)
    val style = SyntaxBinaryCodec<HighlightStyle>(styleShape)
    val ansiColorLevel = SyntaxBinaryCodec<ANSIColorLevel>(levelShape)
    val formatOptions = SyntaxBinaryCodec<FormatOptions>(optionsShape)
}
