# Generic Many Eq/Ord/Show/Read

This checkpoint implements all sixteen class members of four generic Builder.Many
instances, plus Prelude.read, for caller-supplied element dictionaries. Accepted
concrete Inlines/Blocks proofs and the earlier 42-member Many algebra checkpoint
are separate. The full 140-export and 350 SOURCE INSTANCE member audit remains
open; acceptance-matrix.json lists the remaining gates.

Original expectations come only from the pinned Builder/containers/GHC instances.
The four native programs capture 2,312 finite/default/shape, 60 demand/NaN,
4,832 Read and 44 parser-demand observations: 7,248 original cases. All four
historical prefixes in historical/initial-native-prefixes.json are authenticated
without changing any earlier expected bytes. Four JVM adapter tests separately
cover shared success/failure, concurrent forcing, caller identity and delayed
method application. They are not additional Haskell parity claims.

The source model uses lazy shared thunks, raw Haskell Char slots, partial/infinite
String views and ordered ReadS alternatives. Eq checks cached Seq lengths first;
Ord uses source relational defaults, including NaN behavior, and extrema retain
caller identity. Show honors caller showList and source list-spine demand. Read
honors independent list/default overrides and context10 inside Seq. A heap
trampoline handles parser continuations and Final stream append/flatMap spines.
Whole read checks only enough complete alternatives to distinguish ambiguity.
It does not expand exponential duplicate parses. Full materializers still have
JVM memory limits; general cyclic GHC heaps/async exceptions are not emulated.

Source attribution is verbatim in OriginalRuntimeAttribution.txt. ReadP credits
Koen Claessen and Glasgow2002; Read1994–2002, Show/Classes1992–2002 and containers
credits are preserved. Full GHC base and ghc-prim license notices are packaged.
Production is Kotlin; native executables are development/test oracles only.

Native build (cached GHC9.12.2 and cabal):

```sh
PATH=/tmp/kotdoc-haskell/ghc/bin:$PATH CABAL_DIR=/tmp/kotdoc-haskell/cabal-state /tmp/kotdoc-haskell/cabal build exe:types-many-derived-reference exe:types-many-derived-demand exe:types-many-derived-read exe:types-many-derived-parser-demand --project-dir=tools/haskell-types-many-derived-reference --builddir=/tmp/kotdoc-types-order-closure-build
```

The read executable consumes read-inputs.jsonl; other executables have no stdin.
Use verify.py with the four executable paths for byte-exact native replay and
--ghc/--builder-hi for the actual compiled Builder dictionary interface.
The source/dependency/packaged-resource bindings are in source-manifest.json.
The separate open legacy Builder resource gate is in consumer-resource-gates.md.

Author final shared gate: Gradle3187 completed in1m54s. Saved XML contains33,300
actual cases with zero failures/errors/skips: new7,253 +Many5,128 +typedRead16,954
+Generic808 +GenericRep1,738 +NFData1,419. Core JAR/installDist match; all111
upstream notices plus root GPL are exact, and all4,344 prior class files are
byte-identical. The80 added classes belong to these two new Kotlin sources.
Source integrity authenticates50 hashes. Independent acceptance is still required.

The33,300-case gate above was NOT accepted: independent review found incomplete
lexP demand when an unexpected Number/String/Char was rejected before scanning.
The exact rejected sources/manifest/probes/33300result are retained under
historical/pre-lex-correction; the immutable oldJAR/XML remain in the recorded
/tmp result bundle. All7,248 old request/expected bytes remain authenticated.

Correction: ManyReadLex is a source structural-token consumer of the complete
lexToken branch family, including rejected numeric/quoted lexemes. It preserves
hex/octal/binary fallback, greedy digits, fractional/exponent failed lookahead,
numeric/control/ASCII/basic escapes, SO/SOH longest matching, empty escapes and
string gaps. Nonstructural lexeme payloads are unused by the structural pattern;
no Integer/exponent/String payload is forced beyond source lexical guards.
OriginalLex.hs is verbatim pinned GHC and its exact2002 attribution is appended
to the packaged original header notice. A fifth independent oracle adds2,801
observations over three grammar contexts, EOF/bottom/bad heads/tails and safe
infinite suffixes, all escape/control branches and4,096-character lexemes.
The four exact review probes now throw lex-tail. Old7,248 +new2,801 +four JVM
adapter tests pass isolated at512MiB/256KiB. Final shared correction gate and
independent re-review remain required. No accepted production module is changed.

Final lexical gate extends the2,801-row prefix without modifying it: all proper
prefixes of the complete escape family, numeric max-Char guards and SO/SOH
continuations produce5,549 original lexical observations. With the unchanged
old7,248, total original parity is12,797; four JVM adapters +integrity yield
12,802 new JUnit cases. All pass isolated at512MiB/256KiB. Lookahead/consume use
Long positions; point(Int) remains a finite compatibility overload and a large
Long-index/short-input JVM check prevents an artificial signed Int truncation.
Final source/regression/package evidence follows after the shared gate.

Corrective shared gate24052 passed38,849 actual JUnit cases with zero failures,
errors or skips: new12,802, priorMany5,128, typedRead16,954, Generic808,
GenericRep1,738 and NFData1,419. All five original executable outputs and actual
Builder interface replay byte-exact;70 source hashes pass. Immutable finalJAR
runs the reviewer's exact four-input helper at128MiB/256KiB and reports lex-tail
for all four. Core/installDist JARs match,112 upstream notices plus rootGPL are
exact, and4,497 unrelated prior classes are byte-identical. Only this uncommitted
Many family changed (49 own class bytes; one added ManyReadLex class).
The rejected33,300-case snapshot remains separate. Awaiting independent re-review.
