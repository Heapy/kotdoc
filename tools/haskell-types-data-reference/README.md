# Data dictionary port — review checkpoint

## Current complete isolated gate (awaiting installation/review)

The current draft covers all 24 source instances and all 14 Data members. This is
336 instance/member slots, not 336 source instances. `acceptance-matrix.json`
links each slot to the original class contract, deriving implementation, Kotlin
implementation and finite/demand evidence. The immutable wider census remains
350 source instances; unrelated Typeable/count/Version/JSON work remains open.

The ten native corpora contain 1003 original response rows. The finite corpus has
92 inputs with 19 separately checked observations each, so the current complete
isolated replay makes 2659 original comparisons. Two additional JVM factory
sharing assertions are not attributed to Haskell. The JUnit registration has ten
corpus-family cases and one source-integrity case: actual JUnit cases are counted
separately from the comparisons inside each family.

Canonical Definition values use schema-valid `DataNode` constructors with
independent field thunks. All 77 Definition constructors, five aliases and Many
are represented. The adapter is distinct from the existing concrete Kotlin AST;
it does not pretend that strict Kotlin constructor calls model unforced Haskell
arguments. Caller-defined Data dictionaries and higher-kinded carriers are public
protocols. Arbitrary carriers implement `GenericRepRoot` for intrinsic WHNF;
callbacks have explicit root/application protocols. No class laws are imposed.

Dependency dictionaries cover Int, Double, Char, Bool, Text, list, tuple2/3,
Maybe, Seq and Map Text. The Map Text finite-key adapter is the required Definition
dependency, not a claim to port every generic Map operation or arbitrary Ord key
dictionary. Casts implement constructor equality needed by these dictionaries;
full Typeable class/runtime reflection coverage remains a separate audit gate.

DataName/sourceMessage expose lazy source character lists. String getters are
explicit finite JVM bridges. Whole list heads, tails, tuple fields, names and
ErrorCall message prefixes retain separate demands. ErrorCall throwing does not
render its message. GHC stack trace/location text and unrestricted Haskell heaps
are not claimed equivalent to JVM exceptions. Numeric primitive fields use
original Int wrapping, Ratio representation and IEEE decoding, including native
non-finite and negative-denominator behavior; no normalization law is invented.

Independent baseline prefixes remain authenticated by historical-prefixes.json
and pre-list-custom-prefix.json. No accepted old fixture or manifest is rewritten.
The original Data/deriving/Real/Float/Integer sources, installed GHC interfaces and
full original licenses are preserved. OriginalAttribution.txt maps exact source
copyright lines to packaged notices.
