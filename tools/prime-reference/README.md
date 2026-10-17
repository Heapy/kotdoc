# Crypto.Number.Prime development reference

Production is Kotlin. These programs execute pinned crypton 2.0.1 only as development oracles.
The whole task 613, OS/SystemDRG, optimized ChaCha backends, other signature operations,
trust/chain validation and the Pandoc/calibre goal remain open.

The complete Prime module is nine exports and six private/table definitions. `firstPrimes`
contains all 420 literal integers, 2 through 2903; native table output is compared entry by entry.
The source is licensed by `licenses/crypton-BSD-3-Clause.txt`; verbatim implementation,
compatibility module and NumberSpec are retained in `original/`.

`prepare-haskell-oracle.py` verifies all 438 crypton implementation/test files and the RAM
manifest before creating a visibility-only Cabal overlay. Seven hidden modules are exposed;
no cryptographic implementation or original test is edited. The actual profile is GHC 9.12.2,
-O2, aarch64 macOS, little endian, integer-gmp flag enabled, integer-gmp 1.1. Therefore
`gmpNextPrime` and `gmpTestPrimeMillerRabin` return `GmpUnsupported`. This does not mean all
GMP helpers are disabled: gcde and fast powmod still use their pinned GMP implementations.

There are distinct kinds of evidence:

* `number-spec` executes the entire original NumberSpec, unchanged: 45 examples, 100 trials
  per property. The new Kotlin tests explicitly execute all seven primality examples,
  the four prime/safe-prime generation properties and the squareRoot consumer. The existing
  NumberSerial, NumberGenerate and Modular tests retain the other NumberSpec mappings.
* `ProcessProbabilistic.hs` changes only the original module header to expose its actual
  process secret. `ProcessPrime.hs` changes only the module header and probabilistic import.
  Thus ordinary MR rows record both the independently observed answer and the process secret.
  The unchanged original public module is also used for the original suite and nonrandom APIs.
* `ObservedPrime.hs` changes only the original module header and probabilistic import.
  `PrimeProbabilistic.hs` keeps the SHA512/first-40-byte/ChaChaDRG derivation expressions,
  supplies a fixed secret per observer process and wraps the actual DRG dictionary to log
  requested lengths and bytes. No primality arithmetic or witness selection is modified.
  The default secret is bytes 0..31; the environment override is solely for deterministic
  replay of the separately captured ordinary-process observations. The observer is explicitly
  not the unchanged original module. `verify.py` compares every recorded result; dictionary
  traces are compared only where actually instrumented. `draws: null` means unobserved,
  never an expected empty trace.
* `preliminary/ordinary-first-observation.jsonl.gz` preserves the first capture. It includes
  ordinary MR1(1729)=True whereas the fixed-secret profile returned False. This is a real
  difference between process secrets, not a changed expected value or a Kotlin defect.
  Final ordinary MR observations carry their own independently captured secret and replay
  exactly under the controlled observer and Kotlin.
* Original Utils Arbitrary instances produce 100 TestDRG/Int0_2901 samples unchanged.
  Each prime row checks both original prime properties, each safe row both safe-prime
  properties, and 100 squareRoot rows use an independent original QuickCheck `choose` input.
  Complete caller and witness DRG request/byte traces and next-state probe bytes are retained.

Important source details are preserved: inclusive odd search (even 2 starts at 3), predicate
only after probable primality, fresh candidate draws after overflow, all round thresholds,
Fermat's inclusive n+1 bases and empty negative-count range, and strict sqrti even for negative
naive inputs. The actual optimized original forces Fermat's Int count before p before a;
bounded bottom probes document that compiler profile. Miller–Rabin ignores tries on even
inputs, reports its n<=3 error first, and consumes the witness list lazily: MR30(2465) uses one
DRG draw, while prime 2909 consumes 30 witnesses plus rejection draws. It must not eagerly
collect every witness before testing. Calls derive a fresh stream per material from one
retained process context. Prime-size failure is represented by a dedicated Kotlin exception
carrying the exact `CryptoError_PrimeSizeInvalid` identity; no shared error enum was changed.

Limits: Integer is represented by BigInteger, appropriate to these original Integer APIs,
not a replacement for the already specialized word engines. No Java probablePrime or
nextProbablePrime is used. Kotlin Int parameters cover a narrower domain than GHC's 64-bit
Int. Actual 32-bit/big-endian/huge allocations and universal Haskell bottom/heap/concurrency
semantics are not established by these finite observations. Physical erasure and constant-time
execution retain the accepted dependency limitations. Different integer-gmp versions select
other source branches and are not claimed covered by the selected fallback profile.

Build with the prepared project in `/tmp/kotdoc-prime-haskell-oracle.project`, using the
pinned development GHC/Cabal environment. Run `number-spec --seed 613 --qc-max-success 100`,
`capture.py`, `capture-extra.py`, `verify.py`, and the source verifier. Captures never invoke
Kotlin. Do not regenerate ordinary expected values to match a Kotlin result.

Final bounded corpus: 14 334 native rows; 14 343 behavioral Kotlin checks before source-pin tests.
Native original NumberSpec log is retained verbatim (45 examples, zero failures).
The independent exact bounded sieve/trial-division check covers 12 954 observations.
