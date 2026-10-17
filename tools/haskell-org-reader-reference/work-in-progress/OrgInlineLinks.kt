// Text.Pandoc.Readers.Org.Inlines and Parsing.General, Pandoc 3.12.1.
// Copyright (C) 2014-2025 Albert Krewinkel; (C) 2006-2024 John MacFarlane.
// GPL-2.0-or-later; original source and notices retained in upstream/pandoc.
package io.heapy.kotdoc.readers.org
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.ast.Builder as B
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.uri.PandocURI
import io.heapy.kotdoc.xml.PandocXml
internal fun OrgGrammar.linkTarget():OP<String> = char('[').then(till1(noneOf("\n\r[]"),char(']'))).map(::chars)
internal fun OrgGrammar.internalLink(target:String,title:Inlines):OF<Inlines> = Future.asksValue{s->if(target in s.anchorIds)B.link("#$target","",title)else B.spanWith(Attr(classes=listOf("spurious-link"),keyValues=listOf("target" to target)),B.emph(title))}
internal fun OrgGrammar.linkToInlines(target:String,title:Inlines):OF<Inlines> = when{target.isEmpty()||target.startsWith('#')->future(B.link(target,"",title));else->OrgShared.cleanLinkText(target)?.let{future(B.link(it,"",title))}?:internalLink(target,title)}
internal fun OrgGrammar.enclosedRaw(start:OP<*>,end:OP<*>):OP<String> {
 val final=till1(noneOf("\n\r"),end).map(::chars).attempt()
 return start.then(final or anyLine().flatMap{first->final.map{first+" "+it}}.attempt()).attempt()
}
internal fun OrgGrammar.explicitOrImageLink():OP<OF<Inlines>> = char('[').then(linkTarget().attempt() or text("[]").map{""}).flatMap{target->
 val custom:OF<String> = Future.asksValue{s->val type=target.substringBefore(':');s.linkFormatters[type]?.invoke(target.substringAfter(':',""))?:target}
 enclosedRaw(char('['),char(']')).flatMap{description->parseFromString(delay{inline()}.many().map(::inlines),description).before(char(']')).map{title->
  custom.flatMapValue{src->val image=OrgShared.cleanLinkText(description)?.takeIf(OrgShared::isImageFilename);if(image!=null)future(B.link(src,"",B.image(image,"",Inlines())))else title.flatMapValue{linkToInlines(src,it)}}
 }}
}.attempt()
internal fun OrgGrammar.selflinkOrImage():OP<OF<Inlines>> = char('[').then(linkTarget()).before(char(']')).map{target->
 val external=OrgShared.cleanLinkText(target)
 when{external==null->if(target.startsWith('#'))future(B.link(target,"",B.str(target)))else internalLink(target,B.str(target));OrgShared.isImageFilename(external)->future(B.image(external,"",Inlines()));else->future(B.link(external,"",B.str(target)))}
}.attempt()
internal fun OrgGrammar.orgAnchor():OP<OF<Inlines>> = anchor().map{id->val solid=buildString{for(c in id.codePoints().toArray())appendCodePoint(if(ParserCharacters.isAlphaNum(c)||c in "_.-:".codePoints().toArray())c else 45)};future(B.spanWith(Attr(identifier=solid),Inlines()))}
internal class OrgSchemeNode(var terminal:Boolean=false,val children:MutableMap<Int,OrgSchemeNode> = mutableMapOf())
private val orgSchemes by lazy{val root=OrgSchemeNode();for(s in PandocURI.schemes){var at=root;for(c in s.codePoints().toArray())at=at.children.getOrPut(RegexCharacters.toLower(c)){OrgSchemeNode()};at.terminal=true};root}
internal fun OrgGrammar.uriScheme():OP<String>{fun go(node:OrgSchemeNode):OP<String> = any().flatMap{c->val child=node.children[RegexCharacters.toLower(c)];if(child==null)fail("not a URI scheme")else {val tail=delay{go(child)}; (if(child.terminal)tail.attempt().option("")else tail).map{chars(listOf(c))+it}}}
 return delay{go(orgSchemes)}.attempt()}
internal fun OrgGrammar.uri():OP<Pair<String,String>> {
 val word=satisfy{ParserCharacters.isAlphaNum(it)||it in "#$%+/@\\_-&=".codePoints().toArray()}
 val percent=char('%').flatMap{c->satisfy{it in 48..57||it in 65..70||it in 97..102}.many1().map{chars(listOf(c)+it)}}.attempt()
 val entity=char('&').then(till1(nonspace(),char(';'))).flatMap{xs->PandocXml.lookupEntity(chars(xs)+";")?.let{pure(it)}?:fail("entity not found")}.attempt()
 val punctuation=(char(',').many1().map(::chars) or satisfy{!ParserCharacters.isSpace(it)&&it!=60&&it!=62}.map{chars(listOf(it))}).attempt()
 val chunk=word.many1().map(::chars) or percent or entity or punctuation.before((word.map{Unit} or percent.map{Unit}).lookAhead()).attempt()
 fun between(l:Char,r:Char)=char(l).then(chunk).before(char(r)).map{"$l$it$r"}.attempt()
 return uriScheme().before(char(':')).before(not(satisfy{it==42||it==95||it==93})).flatMap{scheme->(between('(',')') or between('{','}') or between('[',']') or chunk).many1().map{it.joinToString("")}.flatMap{body->char('/').map{"$body/"}.option(body)}.map{body->val original=scheme+":"+PandocXml.fromEntities(body);original to PandocURI.escapeURI(original)}}.attempt()
}
internal fun OrgGrammar.plainLink():OP<OF<Inlines>> = uri().map{(original,url)->future(B.link(url,"",B.str(original)))}.attempt()
internal fun OrgGrammar.angleLink():OP<OF<Inlines>> = char('<').then(plainLink()).before(char('>')).attempt()
internal fun OrgGrammar.linkOrImage():OP<OF<Inlines>> = (explicitOrImageLink() or selflinkOrImage() or angleLink() or plainLink()).label("link or image")
internal fun OrgGrammar.inlineCodeBlock():OP<OF<Inlines>> {
 val option=argKey().flatMap{key->skipSpaces().then(not(char(':'))).then(take1{it !in "\t\n\r ]".codePoints().toArray()}).before(skipSpaces()).attempt().option("yes").map{key to it}}.attempt()
 return text("src_").then(argWord()).flatMap{lang->char('[').then(till(option,char(']'))).option(emptyList()).flatMap{opts->char('{').then(till1(noneOf("\n\r"),char('}'))).map{code->future(if(OrgShared.exportsCode(opts))B.codeWith(Attr(classes=listOf(OrgShared.translateLang(lang)),keyValues=OrgShared.originalLang(lang)+opts),chars(code))else Inlines())}}}.attempt()
}
