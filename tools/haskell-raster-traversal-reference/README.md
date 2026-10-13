# Original Applicative image traversal oracle

Builds unchanged JuicyPixels 3.3.9 with GHC 9.12.2 and frozen dependencies. Every
expectation comes from original imagePixels/imageIPixels and original base
Applicative instances: Identity, IO, Maybe, lists and Const over list/Any/Last/a
bracket monoid. No traversal, allocation, pixel-write or Applicative implementation
is copied into the adapter. Supplied callbacks calculate deterministic integer
fingerprints and generated pixel components; original Pixel methods construct and
serialize pixels. Float components travel as raw Word32 patterns.

Requests are (operation,sourceType,targetType,effect,callbackMode,observationMode,
parameters,sourceComponents). Parameters are width,height,sliceOffset,sliceLength,
failingX,failingY. Metadata observations demand image dimensions and the selected
effect structure, without demanding output imageData. Bad-data observations use
an exception instead of the source vector. Const records values without making
an image; Any/Last expose different short-circuit behavior. The bracket monoid
is intentionally non-associative, exposing the original left association.
IO records action order and failure prefixes, including failures demanded only
when pixel values or the output buffer are needed. Lists retain all Cartesian
branches in original order.

Source modules use -O2. Only this adapter disables automatic specialization and
SpecConstr to avoid duplicating its many source/destination/effect dispatches;
original source and algorithms remain unchanged. The original test runner has no
imagePixels/imageIPixels assertions, so independent original calls supply this
new traversal coverage instead of claiming literal upstream tests exist.

Captures exclude native unsafe reads/writes outside storage and observations of
uninitialized output data. Negative and overflowing dimensions are observed with
Const, metadata and defined empty traversals. Identity metadata need not even
construct the traversal's effects, so extremely large indexed shapes can be
observed safely there. Effects that would actually traverse such indexed shapes
are excluded as impractically non-returning; wrapped linear products that are
zero remain observable for every effect. Physical JVM array capacity remains a
separate platform limit.

Cross-type Int64-minimum-height captures demand data and metadata for every
source/destination/effect combination. All source component products are
nonpositive, so no unsafe source read occurs; odd destination component counts
produce original negative-allocation failures only when output data is demanded.

Additional original pixelFoldMap/Last observations check that invalid initial
callback values are skipped in favor of the final pixel, including empty and
both-negative linear dimensions. These expose premature left-operand evaluation.
