# Portable SHA-1 / SHA-224 / SHA-256 source checkpoint

Production translates crypton 2.0.1 `crypton_sha1.c`, `crypton_sha256.c` and the
shared hash-prefix template. `original/cbits` contains byte-identical pinned
files. C and Haskell programs are development oracles only. No JDK hash
implementation is used by these production modules.

The complete portable matrix is 22 C definitions (12 public entries and ten
private definitions), plus all seven shared mask helpers: 29 total. There are
89 literal IV/K words. SHA224 update aliases SHA256 update; its finalizers use
a 32-byte temporary and copy exactly 28 bytes. `SourceMatrix.json` records every
entry and the distinct optimized-backend gates.

## Evidence and reproduction

* `python3 tools/extract-sha32-reference.py --check` checks unchanged C copies and
  exact mechanical round-observer transformations. The observer adds snapshots
  after the original round macros; no arithmetic statement is replaced.
* `python3 tools/sha32-reference/capture-native.py --verify` replays 4,640 ordinary
  original-C observations, including all raw operations and mask helpers.
* `capture-counter.py --verify` adds 1,980 original-C counter-wrap/alignment cases.
* `capture-extra.py --verify` compares 540 defined output aliases and 120 full
  round/schedule traces. The latter use the explicitly instrumented observer;
  ordinary expected bytes remain from unchanged C.
* Native and observer replay also passed `--optimization=-O0` and `-O3` where
  supported by the capture command. Compiler/architecture flags are recorded
  in `CompilerProfile.json`.
* `capture-boundary.py --verify` observes 7,440 virtual prefix-control states from
  copied unsigned C control expressions around the signed JVM length boundary.
  It executes no large allocation or hash. Kotlin uses Long loop positions and
  narrows only for source uint32 masks or a proven in-bounds real array access.
* `capture-haskell.py --oracle /tmp/kotdoc-sha32-haskell-oracle --verify` replays
  2,310 actual public Haskell observations (including 900 generator samples and
  mutable reset/repeated-finalize), 18 verbatim literal results and 36 demand
  observations. The original HashSpec partition runs 27 examples: 18 literals
  and nine chunk/prefix/hybrid properties, each with 100 trials. This is not the
  complete HashSpec suite. The original helper/literal bodies remain verbatim;
  only module exports and the selected `spec` partition are changed.
* `python3 tools/import-sha32-fixtures.py --check` verifies packaged fixtures,
  every literal constant, verbatim test bodies and the current source bindings.

The Haskell oracle was built with GHC 9.12.2 `-O2` against the existing pinned
crypton 2.0.1 development package (explicitly hiding crypton 2.2.0). Its actual
Cabal backend flags differ from the forced portable C wrapper and are recorded.
A build command is in `BuildHaskell.sh`; existing development package setup is
required. Expected values are captured exclusively from originals/literals.

## Domains and limits

Native raw layout is little-endian aarch64: SHA1 C/public context 96 bytes;
SHA224/256 C context 168 bytes versus Haskell allocation 192. Kotlin raw init
writes exactly C's initialized span, preserving guards and the 24-byte tail.
Public debug comparisons exclude that unspecified Haskell tail. A zeroed JVM
allocation is not a claim of parity for uninitialized native heap bytes.

Counters are uint64 byte counters, words and prefix masks are uint32, with
source modular overflow. JVM byte-array input lengths are 0..Int.MAX_VALUE;
negative public prefix lengths undergo source uint32 conversion and clamping.
Full valid JVM prefix control arithmetic is covered without claiming actual
2 GiB allocation/hash execution. Inputs beyond the JVM byte-array length domain
or native invalid pointers, insufficient allocations, and overlapping memcpy
arguments are not supported parity domains. Generic chunk pointers are aligned;
update's unaligned-copy path is exercised at offsets 0,1,2,3,7. Defined byte
output aliases include input and context buffer/state overlap, without invoking
an overlapping memcpy. Digest Data reflection remains part of the wider shared hash-type integration.
No constant-time, secure erasure, heap or universal
Haskell-bottom/timing proof is claimed. Deferred public carriers record bounded
source demand/error order; values and synchronous failures share one result.

ARMv8 SHA1/SHA2 intrinsics, x86 SHA-NI, CRYPTOGAMS/ASM dispatch and their runtime
CPU/global-cache implementations remain separate gates. This checkpoint does
not port HMAC, RSA padding/signatures or finish task 613. OS/SystemDRG, other
signature families, trust/chain and the wider task matrix remain open.
