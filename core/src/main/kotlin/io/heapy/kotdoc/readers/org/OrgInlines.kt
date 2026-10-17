// Text.Pandoc.Readers.Org.Inlines, Copyright (C) 2014-2025 Albert Krewinkel.
// GPL-2.0-or-later; original source and notices retained in upstream/pandoc.
package io.heapy.kotdoc.readers.org
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.parsing.*
internal fun OrgGrammar.inline():OP<OF<Inlines>> = any().lookAhead().flatMap{c->
 val parser=if(!isOrgStrEnd(c))when(c){99->orgCitation() or plainLink() or orgStr();115->plainLink() or inlineCodeBlock() or orgStr();else->if(ParserCharacters.isAlpha(c))plainLink() or orgStr()else orgStr()}
 else choice(listOf(orgWhitespace(),orgLinebreak(),orgCitation(),orgFootnote(),linkOrImage(),orgAnchor(),inlineCodeBlock(),orgStr(),orgEndline(),orgSubscript(),orgSuperscript(),emphasizedText(),orgCode(),orgMath(),orgDisplayMath(),orgVerbatim(),orgInlineLaTeX(),exportSnippet(),orgMacro(),orgSmartQuotes(),orgSpecialStrings(),orgSymbol()))
 parser.before(state().flatMap{s->guard(s.user.emphasisNewlines?.let{it>=0}!=false)})
}.label("inline")
internal fun OrgGrammar.orgInlines():OP<OF<Inlines>> = delay{inline()}.many1().map{inlines(it).mapValue(Inlines::trim)}
