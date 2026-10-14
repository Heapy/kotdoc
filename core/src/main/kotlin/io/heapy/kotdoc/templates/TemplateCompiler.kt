// Text.DocTemplates.Parser 0.11.1, Copyright (C) 2009-2019 John MacFarlane.
// BSD-3-Clause; see licenses/Doctemplates-BSD-3-Clause.txt.
package io.heapy.kotdoc.templates

import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.layout.Doc
import io.heapy.kotdoc.parsing.*
import java.nio.charset.StandardCharsets
import java.nio.file.Path

fun interface PartialResolver {
    fun getPartial(path: String): String
    companion object {
        val Empty = PartialResolver { "" }
        val Files = PartialResolver { path -> java.nio.file.Files.readString(Path.of(path), StandardCharsets.UTF_8) }
    }
}
sealed interface CompiledTemplate<out A> {
    data class Success<A>(val template: Template<A>) : CompiledTemplate<A>
    data class Failure(val error: String) : CompiledTemplate<Nothing>
}
sealed interface AppliedTemplate<out A> {
    data class Success<A>(val doc: Doc<A>) : AppliedTemplate<A>
    data class Failure(val error: String) : AppliedTemplate<Nothing>
}
private data class TemplateParseState(
    val templatePath: String, val partialNesting: Long = 1, val breakingSpaces: Boolean = false,
    val firstNonspace: SourcePos, val nestedColumn: Long? = null, val insideDirective: Boolean = false,
)
private typealias P<A> = Parsec<TemplateParseState, A>

class TemplateCompiler<A : Any>(
    val values: TemplateValues<A>,
    val partials: PartialResolver = PartialResolver.Empty,
    val paths: PartialPaths = PartialPaths.System,
) {
    private val layout get() = values.layout
    fun compile(path: String, text: String): CompiledTemplate<A> {
        val position = SourcePos(path)
        val initial = ParseState(ParseInput(TextChars.fromString(text)), position, TemplateParseState(path, firstNonspace = position))
        val r = template().before(Parsec.eof()).run(initial)
        return if (r.success) CompiledTemplate.Success(checkNotNull(r.value)) else CompiledTemplate.Failure(r.error.toString())
    }
    fun <B> apply(path: String, text: String, context: B, converter: ToContext<A, B>): AppliedTemplate<A> =
        when (val compiled = compile(path, text)) {
            is CompiledTemplate.Failure -> AppliedTemplate.Failure(compiled.error)
            is CompiledTemplate.Success -> AppliedTemplate.Success(TemplateRenderer(values).render(compiled.template, context, converter))
        }
    /** Original IO convenience: the root and its partials are read from files. */
    fun compileFile(path: String): CompiledTemplate<A> = TemplateCompiler(values, PartialResolver.Files, paths)
        .compile(path, PartialResolver.Files.getPartial(path))
    private fun <T> pure(value: T): P<T> = Parsec.pure(value)
    private fun <T> defer(f: () -> P<T>): P<T> = Parsec.defer(f)
    private fun ch(c: Char): P<Int> = Parsec.char(c.code)
    private fun str(s: String): P<String> = Parsec.string(s)
    private fun sat(f: (Int) -> Boolean): P<Int> = Parsec.satisfy(f)
    private fun state(): P<ParseState<TemplateParseState>> = Parsec.state()
    private fun user(f: (TemplateParseState) -> TemplateParseState): P<Unit> = Parsec.modify { it.copy(user = f(it.user)) }
    private fun position(p: SourcePos): P<Unit> = Parsec.modify { it.copy(position = p) }
    private fun input(p: ParseInput): P<Unit> = Parsec.modify { it.copy(input = p) }
    private fun guard(condition: Boolean): P<Unit> = Parsec.guard(condition)
    private fun cps(points: List<Int>) = buildString { points.forEach(::appendCodePoint) }
    private fun literal(s: String): Template<A> = Template.Literal(layout.literal(values.fromText(s)))
    private fun concat(xs: List<Template<A>>) = xs.asReversed().fold<Template<A>, Template<A>>(Template.Empty) { acc, t -> t + acc }
    private fun spaceOrTab(): P<Int> = sat { it == 32 || it == 9 }
    private fun lineEnding(): P<String> = str("\n") or str("\r\n").attempt() or str("\r")
    private fun newlineOrEof(): P<Unit> = lineEnding().map { Unit } or Parsec.eof()
    private fun template(): P<Template<A>> = comment().skipMany().then(
        (lit() or newline() or directive() or escape()).before(comment().skipMany()).manyAlternative().map(::concat))
    private fun endline(): P<String> = lineEnding().flatMap { nls -> state().flatMap { s ->
        val col = s.user.nestedColumn
        val nested = if (col == null) pure(Unit) else
            state().flatMap { guard(it.position.column < col).then(ch(' ') or ch('\t')) }.skipMany()
                .then(state()).flatMap { guard(s.user.insideDirective || it.position.column >= col) }
        nested.map { nls }
    } }.attempt()
    private fun blankLine(): P<Template<A>> = lineEnding().map(::literal).before(newlineOrEof().lookAhead()).attempt()
    private fun newline(): P<Template<A>> = endline().flatMap { nls ->
        (ch(' ') or ch('\t')).many().flatMap { spaces -> state().flatMap { s ->
            user { it.copy(firstNonspace = s.position) }.map {
                if (s.user.breakingSpaces) Template.Literal(layout.space) else literal(nls + cps(spaces))
            }
        } }
    }.attempt()
    private fun lit(): P<Template<A>> = sat { it != 36 && it != 10 && it != 13 }.many1().flatMap { points ->
        state().flatMap { s ->
            val update = if (points.all { it == 32 || it == 9 } && s.position.line == 1L)
                user { it.copy(firstNonspace = s.position) } else pure(Unit)
            update.map { if (s.user.breakingSpaces) breakable(cps(points)) else literal(cps(points)) }
        }
    }
    private fun breakable(text: String): Template<A> {
        val docs = mutableListOf<Template<A>>()
        var at = 0
        while (at < text.length) {
            val blank = text[at] in " \n\r\t"; val start = at++
            while (at < text.length && (text[at] in " \n\r\t") == blank) at++
            docs.add(if (blank) Template.Literal(layout.space) else literal(text.substring(start, at)))
        }
        return concat(docs)
    }
    private fun escape(): P<Template<A>> = str("$$").before(state().flatMap { position(it.position.copy(column = it.position.column - 1)) })
        .attempt().map { literal("$") }
    private fun directive(): P<Template<A>> = conditional() or forLoop() or reflow() or nested() or interpolate() or barePartial()
    private fun <T> enclosed(parser: P<T>): P<T> = open().flatMap { close ->
        spaceOrTab().skipMany().then(parser).before(spaceOrTab().skipMany()).before(close)
    }.attempt()
    private fun <T> parens(parser: P<T>): P<T> = ch('(').then(parser).before(ch(')'))
    private fun inside(parser: P<Template<A>>): P<Template<A>> = state().flatMap { s ->
        user { it.copy(insideDirective = true) }.then(parser).before(user { it.copy(insideDirective = s.user.insideDirective) })
    }
    private fun conditional(): P<Template<A>> = enclosed(str("if").then(parens(variable())).attempt()).flatMap { v ->
        inside(skipEndline().map { true }.option(false).flatMap { multiline ->
            defer(::template).flatMap { yes ->
                (elseBranch(multiline) or defer(::elseIf)).option(Template.Empty).flatMap { no ->
                    enclosed(str("endif")).then(if (multiline) skipEndline().optional() else pure(Unit))
                        .map { Template.Conditional(v, yes, no) }
                }
            }
        })
    }
    private fun elseBranch(multiline: Boolean): P<Template<A>> = enclosed(str("else"))
        .then(if (multiline) skipEndline().optional() else pure(Unit)).then(defer(::template))
    private fun elseIf(): P<Template<A>> = enclosed(str("elseif").then(parens(variable())).attempt()).flatMap { v ->
        skipEndline().map { true }.option(false).flatMap { multiline -> defer(::template).flatMap { yes ->
            (elseBranch(multiline) or defer(::elseIf)).option(Template.Empty).map { no -> Template.Conditional(v, yes, no) }
        } }
    }
    private fun skipEndline(): P<Unit> = endline().then(
        (ch(' ') or ch('\t')).skipMany().then(state()).lookAhead().flatMap { s -> user { it.copy(firstNonspace = s.position) } })
    private fun reflow(): P<Template<A>> = enclosed(ch('~')).then(user { it.copy(breakingSpaces = !it.breakingSpaces) }).map { Template.Empty }
    private fun nested(): P<Template<A>> = state().flatMap { initial -> enclosed(ch('^')).then(state()).flatMap { old ->
        user { it.copy(nestedColumn = initial.position.column) }.then(defer(::template)).flatMap { first ->
            blankLine().many1().map(::concat).flatMap { blanks -> defer(::template).map { blanks + it } }.attempt().many()
                .before(user { it.copy(nestedColumn = old.user.nestedColumn) }).map { rest -> Template.Nested(first + concat(rest)) }
        }
    } }
    private fun forLoop(): P<Template<A>> = enclosed(str("for").then(parens(variable())).attempt()).flatMap { v ->
        inside(skipEndline().map { true }.option(false).flatMap { multiline -> defer(::template).flatMap { body ->
            enclosed(str("sep")).then(if (multiline) skipEndline().optional() else pure(Unit))
                .then(defer(::template)).option(Template.Empty).flatMap { separator ->
                    enclosed(str("endfor")).then(if (multiline) skipEndline().optional() else pure(Unit))
                        .map { Template.Iterate(v, body, separator) }
                }
        } })
    }
    private fun interpolate(): P<Template<A>> = state().flatMap { start ->
        open().flatMap { close -> spaceOrTab().skipMany().then(variable()).before(ch('(').notFollowedBy(Parsec.Companion::showChar))
            .map { close to it } }.attempt().flatMap { (close, v) ->
            (ch(':').then(partialName()).flatMap { partial(v, it) } or
                separator().map { Template.Iterate(v, Template.Interpolate(Variable(listOf("it"))), it) as Template<A> } or
                pure(Template.Interpolate(v))).before(spaceOrTab().skipMany()).before(close).flatMap { handleNesting(false, start.position, it) }
        }
    }
    private fun handleNesting(eatEndline: Boolean, pos: SourcePos, t: Template<A>): P<Template<A>> = state().flatMap { s ->
        newlineOrEof().lookAhead().map { true }.option(false).flatMap { end ->
            val begin = s.user.firstNonspace == pos
            (if (eatEndline && begin) skipEndline().optional() else pure(Unit)).then(state()).map { after ->
                if (begin && end && t !is Template.Nested && pos.column != 1L && pos.column != after.user.nestedColumn) Template.Nested(t) else t
            }
        }
    }
    private fun barePartial(): P<Template<A>> = state().flatMap { start ->
        open().flatMap { close -> spaceOrTab().skipMany().then(partialName()).map { close to it } }.attempt().flatMap { (close, path) ->
            partial(null, path).before(spaceOrTab().skipMany()).before(close).flatMap { handleNesting(true, start.position, it) }
        }
    }
    private fun partialName(): P<String> = (alphaNum() or sat { it in listOf(95,45,46,47,92) })
        .many1().before(str("()")).map(::cps).attempt()
    private fun partial(v: Variable?, file: String): P<Template<A>> = state().flatMap { old ->
        separator().option(Template.Empty).flatMap { sep -> state().flatMap { s ->
            val path = paths.resolve(s.user.templatePath, file)
            Parsec.lift<TemplateParseState, String> { partials.getPartial(path).removeSuffix("\n") }.flatMap { text ->
                val contents = if (s.user.partialNesting > 50) pure(literal("(loop)")) else
                    position(SourcePos(path)).then(input(ParseInput(TextChars.fromString(text))))
                        .then(user { it.copy(partialNesting = it.partialNesting + 1, nestedColumn = null) })
                        .then(defer(::template).before(Parsec.eof()))
                        .before(user { it.copy(partialNesting = s.user.partialNesting) })
                        .before(input(s.input)).before(position(s.position))
                contents.before(user { old.user }).flatMap { t -> pipe().manyAlternative().map { fs ->
                    val p: Template<A> = Template.Partial(fs, t)
                    if (v == null) p else Template.Iterate(v, p, sep)
                } }
            }
        } }
    }
    private fun separator(): P<Template<A>> = ch('[').then(sat { it != 93 }.many()).before(ch(']')).map { literal(cps(it)) }
    private fun comment(): P<Unit> = state().flatMap { s -> str("$--").attempt().then(sat { it != 10 }.skipMany())
        .then(if (s.position.column == 1L) newlineOrEof() else pure(Unit)) }
    private fun open(): P<P<Unit>> = ch('$').before((ch('$') or ch('{')).notFollowedBy(Parsec.Companion::showChar)).attempt()
        .map { ch('$').map { Unit } } or str("\${").before(ch('}').notFollowedBy(Parsec.Companion::showChar)).attempt()
        .map { ch('}').attempt().map { Unit } }
    private fun variable(): P<Variable> = (identPart() or str("it").attempt()).flatMap { first ->
        ch('.').then(identPart()).many().flatMap { rest -> pipe().many().map { Variable(listOf(first) + rest, it) } }
    }
    private fun alphaNum(): P<Int> = sat(ParserCharacters::isAlphaNum).label("letter or digit")
    private fun letter(): P<Int> = sat(ParserCharacters::isAlpha).label("letter")
    private fun pipe(): P<Pipe> = ch('/').then(letter().many1()).before(letter().notFollowedBy(Parsec.Companion::showChar)).flatMap { name ->
        when (val text = cps(name)) {
            "uppercase" -> pure(Pipe.ToUppercase); "lowercase" -> pure(Pipe.ToLowercase); "pairs" -> pure(Pipe.ToPairs)
            "length" -> pure(Pipe.ToLength); "alpha" -> pure(Pipe.ToAlpha); "roman" -> pure(Pipe.ToRoman)
            "reverse" -> pure(Pipe.Reverse); "first" -> pure(Pipe.FirstItem); "rest" -> pure(Pipe.Rest)
            "last" -> pure(Pipe.LastItem); "allbutlast" -> pure(Pipe.AllButLast); "chomp" -> pure(Pipe.Chomp); "nowrap" -> pure(Pipe.NoWrap)
            "left", "right", "center" -> width().flatMap { n -> borders().map { b ->
                Pipe.Block(when (text) { "left" -> Alignment.LeftAligned; "right" -> Alignment.RightAligned; else -> Alignment.Centered }, n, b)
            } }
            else -> Parsec.fail("Unknown pipe $text")
        }
    }
    private fun width(): P<Long> = sat(ParserCharacters::isSpace).label("space").many1()
        .then(sat(ParserCharacters::isDigit).label("digit").many1()).map { ds -> ds.fold(0L) { n, d -> n * 10 + d - 48 } }
        .attempt().label("integer parameter for pipe")
    private fun borders(): P<Border> {
        val spaces = sat(ParserCharacters::isSpace).label("space").skipMany()
        val border = ch('"').then((sat { it != 34 && it != 92 } or ch('\\').then(sat { true })).many())
            .before(ch('"')).before(spaces).map(::cps)
        return spaces.then(border.option("")).flatMap { left -> border.option("").map { right -> Border(left, right) } }
    }
    private fun identPart(): P<String> = letter().flatMap { first ->
        sat { ParserCharacters.isAlphaNum(it) || it == 95 || it == 45 }.many().map { cps(listOf(first) + it) }
    }.flatMap { part -> guard(part !in listOf("if","else","endif","elseif","for","endfor","sep","it")).map { part } }.attempt()

    companion object {
        val Text = TemplateCompiler(TemplateValues.Text)
        val String = TemplateCompiler(TemplateValues.String)
    }
}
