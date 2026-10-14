// Skylighting.Core and Skylighting.Parser 0.15, Copyright (c) 2016-2024 John MacFarlane.
// BSD-3-Clause; licenses/Skylighting-core-BSD-3-Clause.txt.
package io.heapy.kotdoc.highlighting

import io.heapy.kotdoc.ast.UnicodeText

object Syntaxes {
    fun addSyntaxDefinition(syntax: Syntax, map: SyntaxMap): SyntaxMap = syntaxMap(map + (syntax.name to syntax))

    fun syntaxByName(map: SyntaxMap, name: String): Syntax? = map[name] ?: run {
        val lower = UnicodeText.toLower(name)
        syntaxMap(map).entries.firstOrNull { UnicodeText.toLower(it.key) == lower }?.value
    }
    fun syntaxByShortName(map: SyntaxMap, name: String): Syntax? {
        val lower = UnicodeText.toLower(name)
        return syntaxMap(map).values.firstOrNull { UnicodeText.toLower(it.shortname) == lower }
    }
    fun syntaxesByExtension(map: SyntaxMap, extension: String): List<Syntax> =
        syntaxesByFilename(map, "*." + extension.removePrefix("."))

    fun syntaxesByFilename(map: SyntaxMap, filename: String): List<Syntax> = syntaxList(
        syntaxMap(map).values.filter { syntax -> syntax.extensions.any { matchesGlob(it, filename) } }
    )
    fun lookupSyntax(language: String, map: SyntaxMap): Syntax? {
        val name = when (language) { "csharp" -> "cs"; "fortran" -> "for"; else -> language }
        return syntaxByName(map, name) ?: syntaxByShortName(map, name) ?: syntaxesByExtension(map, name).firstOrNull()
    }

    /** Only '*' is special. '?' and bracket expressions are literal characters. */
    internal fun matchesGlob(pattern: String, filename: String): Boolean {
        val input = filename.codePoints().toArray()
        var previous = BooleanArray(input.size + 1); previous[0] = true
        for (char in pattern.codePoints().toArray()) {
            val current = BooleanArray(input.size + 1)
            if (char == '*'.code) {
                current[0] = previous[0]
                for (i in input.indices) current[i + 1] = previous[i + 1] || current[i]
            } else for (i in input.indices) current[i + 1] = previous[i] && char == input[i]
            previous = current
        }
        return previous.last()
    }

    /** Resolve only the context's top-level Keyword rules, as in Parser.hs. */
    fun resolveKeywords(map: SyntaxMap, syntax: Syntax): Syntax {
        fun flatten(items: List<ListItem>): List<String> {
            val output = mutableListOf<String>()
            val stack = ArrayDeque<Iterator<ListItem>>(); stack.addLast(items.iterator())
            while (stack.isNotEmpty()) {
                val iterator = stack.last()
                if (!iterator.hasNext()) { stack.removeLast(); continue }
                when (val item = iterator.next()) {
                    is ListItem.Item -> output.add(item.text)
                    is ListItem.IncludeList -> map[item.context.syntax]?.lists?.get(item.context.context)?.let { stack.addLast(it.iterator()) }
                }
            }
            return output
        }
        return syntax.withContexts(syntax.contexts.mapValues { (_, context) ->
            context.withRules(context.rules.map { rule ->
                val matcher = rule.matcher
                val unresolved = (matcher as? Matcher.Keyword)?.words as? KeywordWords.Unresolved
                val items = unresolved?.let { syntax.lists[it.name] }
                if (matcher is Matcher.Keyword && items != null) rule.withMatcher(
                    Matcher.Keyword(matcher.attributes, KeywordWords.Resolved(WordSet.text(matcher.attributes.caseSensitive, flatten(items))))
                ) else rule
            })
        })
    }

    /** Preserves first occurrence order, ignores child rules and missing contexts. */
    fun missingIncludes(syntaxes: List<Syntax>): List<ContextName> {
        val names = syntaxes.map { it.name }.toSet()
        val missing = linkedSetOf<ContextName>()
        for (syntax in syntaxes) for (context in syntax.contexts.values) for (rule in context.rules) {
            val include = rule.matcher as? Matcher.IncludeRules ?: continue
            if (include.context.syntax !in names) missing.add(ContextName(syntax.name, include.context.syntax))
        }
        return syntaxList(missing)
    }
}
