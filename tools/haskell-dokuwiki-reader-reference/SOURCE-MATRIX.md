# Reader source / branch / test matrix

Pinned Pandoc e51c9c6054c8f4ec5c3209d5abe10939dfe2963e, pandoc-types
b2cbcefc4547296f47b8120740997a9c021d756f. Complete reader: 573 lines,
SHA256 3811d6c3f86eeac5f218a3624a00626b0be7fc3f13dff9b710b253d4702fd5c4.
All functions below are translated in DokuWikiReader.kt unless stated otherwise.
Original115 means every leaf of Tests.Readers.DokuWiki, not a selected subset.

| Original functions | Branches preserved | Independent evidence |
| --- | --- | --- |
| readDokuWiki, parseDokuWiki, eol, guardColumnOne | options/state, EOF, sources, blank lines, parse errors | original115; options/all-fields; sources; malformed; trace/chunks,nul |
| codeLanguage, codeTag | code/file, optional language, whitespace, missing terminator | original115 code tests; branch/code families |
| inline', inline'', inlineUnconsolidatedWhitespace, inline | order/backtracking, consolidated whitespace, smart fallback | original115; all four option variants; 300 bounded-mixed cases |
| endline, whitespace, br, linebreak | block lookahead, soft/hard breaks, mandatory whitespace | original115; branch whitespace/block boundaries |
| between, enclosed, nestedInlines, nestedText | closing delimiter guard, nesting, exact failure Show | original115; malformed; deep/subscript1024,note512 |
| bold, italic, underlined, nowiki, percent, monospaced, subscript, superscript, deleted, footnote, inlineRaw, math | every delimiter, raw HTML/TeX, dollar/backslash extensions, empty and unterminated | original115 including all math groups/comments; formatting/raw/math branch families; deep/percent20000 |
| makeLink, autoEmail, autoLink | URI recognition/trimming, entity decode, email syntax | original115; url cases and mixed corpus |
| notoc, nocache, str, symbol | discarded magic words, stop characters, Unicode scalar consumption | original115; magic-word branches; deep/symbol10000 |
| link, parseLink, linkText | empty/nonempty description, nested formatted description, image link, malformed | original115; link families and mixed corpus |
| isExternalLink, isAbsolutePath, normalizeDots, normalizeInternalPath, normalizePath, urlToText | scheme/path, leading colon/dot, repeated separator, underscores | original115; path/link branch families |
| splitInterwiki, interwikiToUrl | all ten mappings, unknown prefix, external priority | original115; interwiki family |
| isWidthHeightParameter, parseWidthHeight, image | alignment from padding; width/height/full-size/linkonly/nolink; alt, query | original115; image dimensions/padding family |
| block, blockElements, horizontalLine | exact ordered block alternatives, trace after success | original115; trace/plain,quote,lookahead,chunks,nul |
| header | levels, optional closing equals, auto/ascii/GFM IDs, duplicate state | original115; header/options; Unicode12 and full predicate domain |
| list, bulletList, orderedList, parseList | type switch, indentation, nested blocks, item order | original115; list branches; deep/list256 |
| indentedCode, blockCode, blockRaw | code/file tags, blank lines, raw html/latex | original115; code/raw; malformed |
| quote | depth transitions, block-code grouping, original lns==lns behavior | original115; quote branches; deep/quote4096 |
| table, tableRows, tableRow, tableRowEnd, tableCellSeparator, tableCell | header/body, empty cells, per-cell padding alignment, final pipes, malformed | original115; table families; deep/table1000 |
| para | inline trimming/consolidation, block lookahead, EOF | original115; all branch and mixed cases |

Shared source dependencies are pinned in manifest: Parsing General/Smart/Math/
State, Sources, Shared, URI, XML, Options/Extensions, PandocMonad, and types
Definition/Builder/Walk. General URI/email/entity behavior is translated narrowly;
math/smart use existing source-backed Kotlin parsers. Quote assembly and parser
execution use stack-safe continuations. Full deep output, errors, logs and trace
are compared with original observations, not Kotlin-generated snapshots.

DokuWikiIdentifiers translates Shared.inlineListToIdentifier/textToIdentifier/
uniqueIdent, preserving Unicode text/emoji/asciify behavior, GFM categories,
leading letters, punctuation, spaces and bounded duplicate suffix search.
The full source predicate domain is pinned, including surrogates/unassigned.

Remaining shared dependency gap: existing CoreIdentifiers uses ICU78.1 Unicode17
classification while pinned GHC9.12.2 uses Unicode16. For header U+1E6C0 original
DokuWiki returns ID `section`; shared Identifiers.uniqueIdent returns U+1E6C0.
All 4803 Unicode17 additions are unassigned to the pinned predicates. The narrow
DokuWiki helper fixes this reader; other readers using CoreIdentifiers still
require a separate source-backed shared correction. Do not close full Pandoc on
this local fix.

CLI scope: explicit Doku reader/writer, format listings/help, connected filename
inference, reader extensions/options, default-charset UTF8/Latin1/tab/source preparation, trace,
standalone/default/custom template. Independent command fixture extraction uses
original Tests.Command and original Markdown parser. Installed verifier covers
10 complete original commands, 83 CLI-applicable original ASTs (32 library cases use two extensions rejected by the original CLI with exit23), five original Old writer
goldens. Pending complete commands: 8182 (RST writer), 5107#1/#2 (Muse reader).
The whole local:618 and full Pandoc/calibre scope remain open.

Application-domain distinction: the 48 original library math cases include
32 single/double-backslash cases. Original CLI Extensions.getAll rejects those
extensions with exit23 before reading input. The remaining83 cases are accepted
through the real CLI. The persistent checker covers both distinct rejected
extension combinations with exact original exit/stdout/stderr, as well as16
complete application observations (BOM/CRLF, tabs/preserve-tabs, smart/math,
standalone/default/explicit templates, variables/wrap, trace and inference).
General application metadata parsing (`-M`) is an existing CLI gap outside this
narrow registration checkpoint; these option observations use supported `-V`.

App.Input review correction: default absent charset attempts strict UTF8, then
checks ZIP/PDF/CFBF/DjVu signatures only upon decoding failure. Non-signature
failures warn and decode the original raw bytes as Latin1 (including any BOM);
CR removal and tab conversion follow decoding. Separate cli-input.json holds35
exact original exit/stdout/stderr observations, including named files, malformed
sequences, BOM/CR, valid signature prefixes and near-miss signature offsets.
The previous1479 reader observations and16 application options are unchanged.

Warning renderer correction: canonical Class/IO.hs150–154 prefixes severity,
joins Data.Text.lines with LF plus two spaces, then writes one final LF.
NotUTF8Encoded always ends in a fixed nonempty sentence, so replacing every
embedded LF by LF plus two spaces is exact for this warning domain. CR and tabs
remain unchanged. Only warning rendering changes; binary InputNotText errors
and trace lines retain their own canonical formatting. Separate cli-warnings.json
contains22 original observations for filenames with LF/doubleLF/CRLF/CR/tab,
Unicode, leading/trailing LF, multiple lines, and chunk; every named case has a
binary-signature error control. Implicit/explicit stdin trace cases cover order.
Original35 input and all prior21 fixture payloads remain unchanged. Use
`check-dokuwiki-cli.py --boundary-only` for22 warning/error/trace +35 input +16
application observations, each comparing exact exit/stdout/stderr.
