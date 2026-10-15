// Text.Pandoc.Translations, © 2017–2024 John MacFarlane, GPL-2.0-or-later.
// Complete original notice and pinned source are retained in licenses/upstream.
package io.heapy.kotdoc.translations

import io.heapy.kotdoc.errors.PandocError
import io.heapy.kotdoc.language.Lang
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.resources.PandocText
import io.heapy.kotdoc.state.PandocState
import io.heapy.kotdoc.yaml.YamlValueResult
import io.heapy.kotdoc.yaml.YamlValues

sealed interface TranslationReadResult {
    data class Success(val value: Translations) : TranslationReadResult
    data class Failure(val error: String) : TranslationReadResult
}

/** Pandoc's readDataFile effect, including its user/default-data selection. */
interface TranslationRuntime : PandocState {
    fun readDataFile(file: CharacterString): ByteArray
}

/** All documents must decode as Translations before the first one is selected. */
fun readTranslations(input: String): TranslationReadResult = when (val result =
    YamlValues.decodeAllAs(TextChars.fromString(input).toByteArray(Charsets.UTF_8),Translations::fromValue)) {
    is YamlValueResult.Failure -> TranslationReadResult.Failure(TextChars.fromPoints(result.error.pretty().points()))
    is YamlValueResult.Success -> result.value.firstOrNull()?.let(TranslationReadResult::Success)
        ?: TranslationReadResult.Failure("empty YAML document")
}

/** Language selection is lazy; an already loaded equal-language table is retained. */
fun PandocState.setTranslations(lang: Lang) = modifyCommonState { state ->
    val cached = state.translations
    if (cached?.first == lang && cached.second != null) state else state.copy(translations = lang to null)
}

fun TranslationRuntime.getTranslations(): Translations {
    val selected = getsCommonState { it.translations } ?: return Translations.empty
    selected.second?.let { return it }
    val lang = selected.first
    val files = listOf(lang.renderLang(),lang.language + (lang.script?.let { "-$it" } ?: ""),lang.language)
        .distinct().map { CharacterStringChars.fromString("translations/$it.yaml") }
    for ((index,file) in files.withIndex()) {
        val text = try { PandocText.toTextM(file,readDataFile(file)) }
            catch (error: PandocError) { null }
        val result = if (text == null) TranslationReadResult.Failure("") else readTranslations(text)
        when (result) {
            is TranslationReadResult.Success -> {
                modifyCommonState { it.copy(translations = lang to result.value) }
                return result.value
            }
            is TranslationReadResult.Failure -> if (index == files.lastIndex) {
                report(LogMessage.CouldNotLoadTranslations(lang.renderLang(),TextChars.fromPoints(file.points()) + ": " + result.error))
                // A report callback may mutate state or throw. Clear only after
                // successful reporting, and preserve all its other state changes.
                modifyCommonState { it.copy(translations = null) }
            }
        }
    }
    return Translations.empty
}

fun TranslationRuntime.translateTerm(term: Term): String {
    getTranslations().lookupTerm(term)?.let { return it }
    report(LogMessage.NoTranslation(term.show()))
    return ""
}
