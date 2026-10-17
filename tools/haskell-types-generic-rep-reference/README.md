# Derived Generic dictionaries (in progress)

This checkpoint is distinct from Text.Pandoc.Generic's four Data traversals.
It targets all 24 source-owned derived GHC.Generic instances: the 23 Definition
types and parameterized Builder.Many. Each has associated Rep and from/to;
Datatype (4), Constructor (3), and Selector (4) metadata methods are separately
represented. Generic1 is not derived by these source declarations and is not
invented here. All 350 source instances still require their explicit member audit.

OriginalGenerics.hs is the unchanged GHC 9.12.2 source; OriginalGenerics.iface.txt
is the actual installed GHC interface. The oracle calls the real derived
instances. Its 85 finite requests cover all source constructors and Many over
Inline/Block, emitting complete sum/product/M1/K1/U1 association, all metadata,
field types and Show values, to(from x), and from(to(from x)). The source package
name includes the pinned build's inplace package identity; no universal installed
package identifier is claimed.

Two new Kotlin modules and independent replay tests have passed the author
isolated gate:1,718 original rows and two JVM adapter checks. It includes all85
finite source/typed cases,820 input/metadata observations,773 direct partial Rep
cases and40 custom/deep/raw IEEE cases. These are named bounded source proofs;
shared regression/package and independent review remain the acceptance gates.
The Rep protocol exposes layer projection separately from WHNF because M1 and
K1 are source newtypes, while sums/products/U1 are data. Fields carry no invented
Eq/Read/Show/Data class constraints; normal-form traversal is not performed.

No shared production files have been installed. Git, PORTING and tracker changes
belong to the independent reviewer. Whole140 exports, 350 source-instance member
expansion, Data.Version, six ToJSONFilter instances and JSON supplement stay open.

## Final author evidence and source model

The complete finite source corpus is 85 rows. Each checks the original full Rep
shape/metadata/K1 value/type, original to(from x) Show value, original
from(to(from x)) shape and the separate existing-AST bridge in both directions.
The 820 independent input/metadata cases include every source field, nested and
competing failures, root from/to, ignored datatype metadata, and Many over Int,
Text, Bool, tuple and function types. There are no element constraints in the
Many instance; Show/Read/Typeable are constraints only of the finite observation
adapter, never production Generic dictionaries.

773 direct partial Rep cases separately exercise every M1/K1/U1/sum/product
position and product-spine versus strict-field races. They exposed an initial
draft bug: TableBody.to forced the first field before matching a bad right
product spine. Historical/product-spine-before.log preserves that failure.
prepareProducts now matches the complete source product pattern before creating
strict fields; it does not force S1/K1 payloads independently. This matches
OriginalDerivGenerics.hs mkProd_P/mk1Sum/foldBal, rather than assuming Walk order.

40 custom/deep cases include caller-defined ignored U1, strict K1, lazy product
dictionaries, 50,000-deep Inline/Many values, raw Seq Single/Deep/tree failures,
and eight raw IEEE Double payloads through K1 and both source/typed from/to.
Historical/many-root-before.log preserves the initial missed deferred tree-root
failure. Shared intrinsic WHNF now demands the Seq tree and Deep's strict digit
roots, without forcing Single cells/measures, middle trees or element payloads.
Previous20/32 custom response prefixes were retained byte-exact.

GenericRepThunk and GenericRepField apply intrinsic source WHNF independently
of custom Generic dictionaries. GenericRepRoot is an open root protocol for custom
source carriers; its implementation represents constructor demand, not NF.
Known Many/ManySequence and accepted deepseq carriers have intrinsic adapters.
GenericRepValue separates lazy newtype layer projection from root WHNF. A
custom dictionary may ignore its input or representation; such ignored values
are not evaluated. Two separately counted JVM checks cover shared values/errors
and dynamic type/layer guards. Ill-typed dynamic carriers have no Haskell
counterpart; guard results are not original semantic expectations.

Existing AST bridges are restricted to fully defined finite values. In
particular legacy count AST fields use Long; distinct source count dictionaries
retain their actual Rep identity. GenericSequence's abstract Data view is used
only in finite bridge cases; partial physical Seq values use Many/ManySequence
root carriers. Raw source Map/tuple/list fields use accepted explicit source
carriers. Ordinary custom JVM classes either have strict construction or supply
GenericRepRoot/a callback implementing source WHNF. No arbitrary GHC heap,
black-hole, infinite value, async/parallel evaluation or compiler-independent
error order is claimed; observed choices are pinned GHC9.12.2/-O2.

Source inventory has 24 associated Rep types and 48 from/to members, plus metadata
bindings for 24 datatypes (four methods), 78 constructors (three methods), and91
selectors (four methods). No source field is unlifted: DecidedUnpack annotations
on boxed Int/Double/count fields still use K1, not URec. Source primitive-type
selection and balanced association are pinned from the actual compiler module.

GHC.Internal.Generics credits Universiteit Utrecht2010–2011 and University of
Oxford2012–2014; deriving code credits The University of Glasgow2011. Exact source
attribution is packaged separately alongside the unchanged full libraries/base
license, which is byte-identical to the existing GHC-base notice. Production is
Kotlin; the four Haskell executables are development-only oracles.

The full semantic shared gate2346 passed24,391 actual cases with zero failures/errors/skips (1,721 new GenericRep +22,670 prior source suites). Final provenance audit identified that compiler deriving code references the separate GHC root LICENSE (2002 University Court), while base LICENSE is2004. Both full notices are retained unchanged, alongside exact Utrecht/Oxford/Glasgow2011 source attribution. A fresh sourceIntegrity/package gate follows this notice-only binding change; semantic XML/source manifest/JAR are preserved separately and all final class bytes must remain identical.

A final lawful custom-instance gate exposed eager dictionary application and newtype-pattern projection in the earlier author snapshot: original bodyM/from-unused returns7, while the prior draft threw input. historical/dictionary-dispatch-before.log, the exact prior main source and frozen semantic manifest authenticate this gap and the unchanged40-case prefix. PandocGenericRep.from/to now defer whole factory dispatch; the from lambda extension uses that source application adapter. GenericRepValue.layer projects M1/K1 lazily, while whnf separately observes erased roots. Dictionary methods are Kotlin implementation factories; custom clients use PandocGenericRep applications for whole-method source demand.

Sixteen appended original observations cover lawful M1/K1 instances with eager factory bodies, ignored whole results, ignored newtype patterns, demanded fields, and four bounded NOINLINE/IORef sharing observations. The instrumented GHC profile reports0:1 for fromM/fromK/toM and0:1:0:1 for lazy toK, rather than assuming callback counts. This is pinned observer evidence, not a universal unsafePerformIO guarantee. The complete isolated gate is1,734 original rows +three separately counted JVM adapters. Prior85/820/773 bytes and40 custom responses are authenticated against the earlier immutable manifest. The pre-dispatch24,391 semantic gate plus one notice/integrity execution remain preserved; a fresh24,408 corrective regression follows the semantic fix.

The corrective shared gate94862 passed24,408 actual cases with zero failures, errors and skips:1,738 GenericRep cases (1,734 original rows, three JVM adapters and one integrity case) plus22,670 prior cases. All60 source pins pass. The final core JAR and installDist JAR are identical; all108 packaged notices match their source bytes, and4,299 non-GenericRep class entries match the preceding license baseline. Earlier semantic and integrity snapshots remain separate historical evidence.
