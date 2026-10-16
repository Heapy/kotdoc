# Exact original typed Read

This dev-only executable invokes actual Read dictionaries of pinned pandoc-types
1.23.1.2, text2.1.2, containers0.7 and GHC9.12.2. Production parsing is Kotlin.
Original56 typed examples are imported from the retained verbatim original module;
no original source, literal, license or EOF byte is rewritten. Native Data census
uses dataTypeConstrs/constrFields and gunfold to retain25 concrete dictionaries,
all79 constructors, actual arities and record field names. Five aliases retain
separate method evidence. The manifest joins every constructor and all five
readsPrec/readPrec/readList/readListPrec/read methods to concrete fixture indices.

16,954 native requests cover all constructor roots, precedence-1/0/10/11 and
machine-Int bounds, optional parentheses, exact constructor/field arity,
canonical delimiter deletions, removed field parentheses, typed separation,
records/declaration order/duplicates/missing fields, list/tuple/Map/Seq/Maybe,
Unicode whitespace and rejected comments, string gaps/escapes/Char-list syntax,
surrogate replacement versus untouched Char remainders, Int64 wrap and IEEE
boundaries/rounding, prefix parsing and full-read errors. Complete native response
lines are retained verbatim as expectedWire; nativeResponsesSha256 reconstructs
all original response bytes, rather than normalizing native numeric JSON.

Important source contracts: positional constructors use Read precedence10;
derived records use11 (retained actual Definition interface guard), even though
record Show parenthesizes above10. Numeric/Bool/nullary/literal/list/tuple Read
ignore precedence; Just and Map/Seq fromList have application precedence10.
Text delegates to Read String then pack, including surrogate replacement. Char's
readList override additionally accepts quoted strings. GHC's Read lexer skips
isSpace, not Haskell comments. Result remainders preserve all trailing Chars,
including spaces, isolated surrogates and supplementary characters.

Actual complete parse-result lists are retained, including duplicate Char-list
parses. Char's readListPrec has nested parens combinators: a bracket list under
n groups yields n+1 equal results. Text inherits this through String Read;
whole read can therefore report ambiguous parse. Quoted strings have one parse.
No parser result is selected/discarded to hide ambiguity.

Deep32/256/2048/8192 cases retain complete native canonical Show and exact
remainder, with value=null explicitly marked lean to avoid recursive JSON
transport limits. Kotlin parsing and Show traversal use explicit continuations.
Ordinary cases check actual native JSON values too; float transport compares IEEE
numeric values because Aeson's Scientific/JVM shortest decimals can differ, and
independent native canonical Show separately preserves signed zero. Actual native
Double/ColWidth raw IEEE words are also checked, including signed NaN/zero and
nullable JSON NaN transport; no NaN sign/payload can disappear in transport. Exact Int64
transport remains integer-exact. Primitive Char is checked by exact ordinal.

Input/output use CharacterString for original Haskell Chars. String overloads use
the existing JVM adapter convention. This covers finite represented values and
successful/rejected syntax; infinite input spines, arbitrary user-defined Read
instances, asynchronous exceptions and a general GHC runtime are not claimed.
Existing NativeReader behavior/fixtures remain unchanged.

```sh
cabal build exe:types-read-reference --project-dir=tools/haskell-types-read-reference --builddir=/tmp/kotdoc-types-read-build
python3 tools/import-types-read-fixtures.py --oracle PATH
python3 tools/import-types-read-fixtures.py --check-source
```

readListPrec is observed via actual readPrec_to_S readListPrec at enclosing
precedences Int64-min/-1/0/10/11/Int64-max. GHC list resets element precedence to
zero; Char additionally accepts quoted strings. Constructor, grouping, malformed
list and exact remainder cases cover every dictionary and alias independently.

Whole-read stress uses2/16/30/31/32/64/256/1024 independent ambiguous fields,
with empty/whitespace tails;2/16-field cases additionally retain nonspace tails.
Large rejected tails would require the native parser to exhaust all alternatives
to prove no result, so they are not captured as fast demand probes. Only actual Prelude.read is invoked;
no enormous ReadS result is serialized. Kotlin whole read saturates ambiguity at
two, preserving no-parse on a nonspace remainder. The ReadS List adapter retains
alternatives in a shared read-only view up to JVM List's Int.MAX_VALUE capacity;
larger result cardinalities are explicitly outside that adapter, not claimed as
universal finite Haskell ReadS parity. Whole read does not have this List limit.
