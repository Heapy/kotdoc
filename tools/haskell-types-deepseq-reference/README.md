# Definition NFData dictionaries

This slice covers the 23 explicit NFData instances in pinned Definition.hs,
with their single rnf member, not all 350 source instances or all 140 exports.
Empty instance declarations select deepseq-1.5.1.0's Generic default. The exact
source, installed GHC interface, license and containers Map implementation are
retained here. The oracle calls the actual linked original dictionaries.

PandocDeepSeq supplies evaluated rnf/rwhnf operations and explicit lazy unit
adapters rnfLazy/rwhnfLazy. force and deepseq return memoized root callbacks.
strictApply represents ($!!): its function receives a thunk argument, because a
custom NFData dictionary may ignore an undefined value and a constant function
may also ignore it. The original custom/strictApply-ignored probe returned 7;
the initial uninstalled eager-function draft instead threw input. Its actual
failure log is preserved under historical/. No accepted production behavior was
rewritten for that correction. Custom NFData dictionaries remain callable and
can independently choose root versus fields. JVM callback/failure sharing has
separate adapter tests, not an invented original exception-identity predicate.

DeepSeqData uses explicit well-typed node/list/tuple/Maybe/rawMap carriers for
partial source values. StrictData and newtype WHNF are applied independently
from NF. Text uses only WHNF, not Data's unpack traversal. Raw Map forces its
strict size/key/subtree roots and rnf traverses key/value/left/right; invalid
cached size bounds are not normalized or rejected. Shared Map spine evaluation
is cached and the engine is iterative. PandocNFData exposes 23 typed finite AST
dictionaries, preserving distinct count wrappers. fromAst is a finite, fully
defined-value bridge. In particular its Map bridge does not claim the original
physical Map.fromList tree or partial-value error choice; use raw Map carriers
for those observations. No fabricated AST placeholders are used.

308 original finite responses cover77 constructor examples and four operations;
both source and typed finite APIs execute them. 680 independent partial cases
cover every immediate field and competing direct/deep/mixed failures. 94 further
original expressions cover custom ignored/root/projected dictionaries, delayed
unit/force results, ($!!) argument demand, Map key/value distinction, malformed
size caches, NaN/infinities, deep values and 50,000-element list/Map/tree cases.
Earlier 354/50/61/65 response prefixes were retained byte-exact when adding cases.
Inputs and Haskell expressions are generated independently of Kotlin. Original
expected responses must never be generated or corrected using Kotlin.

Control.DeepSeq explicitly does not guarantee evaluation order across compilers
or parallel implementations. The observed choices are pinned GHC 9.12.2/-O2
finite proofs; the Kotlin adapter uses the corresponding sequential traversal.
This is not arbitrary Haskell heap, black-hole, infinite input, parallel runtime
or physical Text-array equivalence. Raw carrier inputs must satisfy source type
signatures. NFData1/NFData2 and unrelated deepseq module APIs are not claimed as
additional pandoc-types instances; Many has no source NFData instance.

Reproduce the development oracle with cached GHC/cabal:

```
cabal build exe:types-deepseq-reference exe:types-deepseq-demand \
 exe:types-deepseq-extended --project-dir=tools/haskell-types-deepseq-reference \
 --builddir=/tmp/kotdoc-types-order-closure-build
python3 tools/haskell-types-deepseq-reference/verify.py --check-source \
 --oracle /path/to/types-deepseq-reference \
 --demand-oracle /path/to/types-deepseq-demand \
 --extended-oracle /path/to/types-deepseq-extended \
 --ghc /tmp/kotdoc-haskell/ghc/bin/ghc
```

Original DeepSeq source/license were extracted without edits from the Hackage
1.5.1.0 tarball. OriginalMapInternal.hs is the unmodified containers-0.7 module.
The full deepseq license is packaged separately alongside existing pandoc-types,
containers and text notices. Production code is Kotlin; Haskell is dev-only.

SourceThunk preserves intrinsic WHNF independently of a custom NFData dictionary. A dictionary may ignore its argument; force or a demanding $!! callback still observes StrictData fields when the returned source value is demanded. The original four strict-root probes distinguish a failing list root from an unrequested failing head. Source carriers model these constructor demands; arbitrary custom JVM classes must encode their own source constructor WHNF in the supplied callback. This is not a general Haskell heap evaluator.

Final isolated gate: 308 source finite, 308 typed finite, 680 partial and 94 extended original observations, plus three JVM sharing/static dictionary adapters. All passed at 512 MiB heap and 256 KiB stack.

Independent review of the first 22,645-case shared gate exposed a missing returned-carrier WHNF boundary. That gate is a failed-coverage baseline, not acceptance. The preserved historical/returned-carrier-before.log shows the new original list-root expectation failing against the prior draft. deepseq now memoizes its output with SourceThunk; strictApply shares that path. The added 24 original probes cover both helpers and returned list roots/heads, raw Map roots/size/left/value, StrictData roots/heads, newtypes, lazy tuple fields, ignored results and input-before-result errors. The earlier 94 extended response prefix is unchanged. Final isolated proof is 1,414 original/typed assertions plus four JVM adapters, including repeated returned-root failure sharing.

Corrective shared gate 30496: 22,670 actual cases, zero failures/errors/skips; NFData contributes 1,419. The exact independent Java return-root reproducer now fails as expected for both helpers against the rebuilt package. All 106 package notices and installed/core JAR equality pass. Historical rejected author output is retained separately; this is not independent acceptance or whole task611 closure.

## Intrinsic root interoperability correction

`DeepSeqData.sourceWHNF` now dispatches the existing open `GenericRepRoot` protocol. `GenericRepIntrinsic.force` reaches it through `SourceThunk`, so its duplicate dispatch is removed. This preserves record field laziness and shares each thunk's success/failure. `IntrinsicRootReference.hs` imports unchanged original crypton/deepseq; its twelve observations are stored verbatim in `intrinsic-root-native.txt`. Host adapter tests additionally cover both rwhnf overloads, SourceThunk, ignored NFData force, returned deepseq/strictApply carriers, single dispatch and shared synchronous failure. No universal heap/timing or asynchronous exception claim.

The rejected Types build remains immutable at `/tmp/kotdoc-rsa-types-final-results`; its XML112366/JAR735d5968 are pre-fix evidence, not acceptance. Exact prior NFData source and manifest are in `historical/pre-intrinsic-root/`; prior Generic manifest in `../haskell-types-generic-rep-reference/historical/pre-deepseq-intrinsic-root/`. Current manifests are rebuilt against actual source; no historical fallback. All native old expectations remain unchanged.
