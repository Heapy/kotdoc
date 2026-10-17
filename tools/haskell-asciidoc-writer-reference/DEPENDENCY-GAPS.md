# AsciiDoc dependency boundaries

This writer checkpoint does not close local:612 or local:621.

* Shared `DocumentShared.makeSections(False, Nothing)` over 1,024 nested Div
  blocks overflows the JVM stack at 256 KiB. The retained
  `shared-make-sections-before.log` and original `sections`/`deep` fixtures
  reproduce the source behavior. `AsciiDocSections` implements only the actual
  False/Nothing/empty-offset closure, including lazy minimum-header demand,
  attribute ordering, merging and overflow behavior. It is not a replacement for
  all makeSections modes.
* Shared `DocLayout.chomp` composition over the 4,096-deep bullet recipe exhausts
  512 MiB; see `shared-chomp-before.log`. Private `asciiDocChomp` retains untouched
  prefix sharing and follows only trailing-spine rules, modulo Concat
  associativity. The 11,154 independent source structures, renderings and metrics
  compare this closure. This is not public Doc structural equality or arbitrary
  deferred/bottom equivalence. Initial strict-reassociation local attempts also
  exhausted the same heap and are retained as failed evidence.
* Source section normalization is not universally idempotent: 140 synthetic
  duplicate-attribute cases change on the second source pass. Both independent
  source captures are retained; the original explicit idempotence case passes.
* `NativeReader.read` currently throws IllegalArgumentException on exhausted
  Read alternatives. Source Native.hs:44 throws PandocParseError; Error.hs:83,188
  renders `couldn't read native` and exits64. The new AsciiDoc adapter maps only
  this exact failure on its own native-input output routes. Other output routes
  retain the existing wrong App exit1/prefix and remain a general621 defect.
  The preserved isolated-cli-proof3 terminal152/183 is the before-fix evidence.
* Full original Old command lines require unsupported global --quiet; four
  independent library goldens and six App-without-quiet controls do not close
  those four commands. `cli-pending.jsonl.gz` retains them plus27 command routes
  requiring absent input readers (one also --verbose).
* Scalar mapping tests use the Haskell Text input domain: T.singleton replaces
  isolated surrogate code points with U+FFFD. Char classifier checks still cover
  every scalar position including surrogate values. Separate Native CLI input
  cases check production text packing; raw JVM surrogate behavior is not inferred
  from helper normalization.
* Nine original demand controls include code/ignored-attrid, which is native-only
  because the Kotlin AST has no deferred String carrier. Eight counterparts run
  in Kotlin; meta/ignored is a StrictData constructor-root control, not a claim
  that the writer evaluates ignored metadata. Seven actually call the writer.
* Original zero-colspec/empty-Row input is nonproductive: source makeRow recurs
  with the same empty row. The observed original child was explicitly terminated
  after CPU-progress confirmation; no productive output or parity is claimed.

Two additional exact general621 controls are retained in
`evidence/general-app-gap-controls.json` with argv, input bytes and both complete
results. Native malformed UTF8 inside a quoted Str is decoded as Latin1 with a
warning by original App.Input, while the existing generic Main path replaces it
with U+FFFD silently. Invalid JSON is original PandocParseError64 versus current
generic exception1. The AsciiDoc output registration does not repair generic
input decoding/JSON parser errors. The normal finite output routes and the32
explicit OutputSettings phase cases must not imply closure of these boundaries.

The first local chomp scan optimization was superseded after a measured repeated
Concat traversal (read-only thread sample retained). Its own proof was explicitly
stopped with SIGTERM15/tool143 after the corrected implementation compiled; it
is not a passing run. The session-owned identity memo processes immutable Concat
nodes once and leaves non-Concat wrappers untouched, retaining unchanged prefix
identity. A fresh full bounded run passes all72 deep original observations.
Cache lifetime is exactly one private Writer Session; no global/static cache,
no equality/hashCode traversal and no mutable-Doc assumptions are introduced.

Eight separate native-only DocLayout demand controls retain Concat left/right/
middle error order and Prefixed root-WHNF versus full-render demand. They are
`chomp-demand-native-only.jsonl.gz`, not Kotlin success claims: the public Kotlin
Doc fields cannot carry those deferred child/String bottoms. Finite concrete
Concat/Prefixed structures and source render/metrics are executed in Kotlin.
