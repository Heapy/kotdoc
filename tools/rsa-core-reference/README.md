# RSA integer and key-generation core

This checkpoint ports all three functions in Crypto.PubKey.RSA and both public
plus four private functions in RSA.Prim. SourceMatrix.json retains every remaining
RSA padding/type-instance/signature and task-613 family. No upstream runtime is
used by production. BigInteger implements Integer arithmetic; Java RSA engines
and probablePrime are not used.

The original crypton 2.0.1 sources and RSASpec are preserved verbatim under
original/. ObservedRsa changes only its module header and Prime import; the latter
uses the already verified fixed-secret Prime observer. ObservedRsaPrim and
RsaVectors change only module headers. The extractor checks those transformations.
BoundedRsa additionally counts outer generate attempts and raises at an explicit
budget. Its result is an instrumented prefix observation, never an ordinary
source returned value or a proof of termination.

The main fixture has 2,481 rows: 2,465 original computations (with header-only
private-helper exposure or fixed-Prime generation observer where applicable)
and 16 explicitly instrumented bounded-loop rows. Separate fixtures hold 100
original Utils.TestDRG seed samples and eight strict-constructor/multiplication
and lazy-size error-order observations.
Kotlin executes both original blinder properties for all 100 samples, three
literal key vectors, and all 40 private-exponent combinations. The unchanged
full original RSASpec ran 37 examples with 100 trials; its padding/signature tests
are supporting oracle evidence, not Kotlin completion of those modules.

RsaDemand supplies a real custom DRG dictionary with pair-root, byte-field and
state-field exceptions. Its 60 original GHC-O2 observations distinguish ignored
values, demanded state and demanded strict Blinder fields. RsaCallback is a
separate effectful MonadRandom dictionary: its first request occurs even for
e=0/1. Pure MonadPseudoRandom can leave all prime generation unused for e<=1
and loop forever. The Kotlin pure adapter uses an explicit non-returning loop
for that branch so it does not replace source divergence with JVM stack overflow.
Unchanged-source two-second noncompletion observations are
retained in preliminary/, alongside the source-derived no-progress explanation.
The failed first batch SIGKILL has no known pending row and is not a passing or
conclusive nontermination result. Later captures persist a per-request cursor.

Source profile: GHC 9.12.2 -O2, aarch64 Darwin, little endian, Int64; the existing
Prime Cabal overlay changes visibility only, with all 438 library files intact.
integer-gmp 1.1 disables nextPrime/MillerRabin fast paths in the pinned Compat
module. Ordinary randomized Prime witnesses use the fixed-secret development
observer; process-secret policy itself belongs to the accepted DRG checkpoint.

The typed CryptoPseudoRandom adapters preserve lazy value/state projections.
Callback overloads deliberately represent an effectful MonadRandom dictionary;
they do not claim pure-State demand equivalence. Synchronous failure sharing is
memoized. These bounded probes do not prove all Haskell bottoms, asynchronous
exceptions, heap timing, constant-time behavior, or erasure. Mutable input byte
arrays are read synchronously; returned arrays and key records are newly owned.
The selected GHC worker demands multiplication modulus, right operand, then left;
strict Blinder fields precede the dpFast body. Separate probes preserve that
profile-specific synchronous demand order. No claim spans all GHC optimizers.
Kotlin array lengths and BigInteger bit indices are Int bounded; checked size
conversion and bit multiplication reject unsupported resource dimensions rather
than silently wrap GHC Int64 inputs. No actual multi-gigabyte allocation proof.

Reproduce source check: python3 tools/extract-rsa-core-reference.py
Reproduce native replay: python3 tools/import-rsa-core-fixtures.py --verify-oracle
Build commands and exact compiler/profile pins are in Environment.json.
All translated sources retain the crypton BSD-3-Clause notice at
licenses/crypton-BSD-3-Clause.txt.

## Strict Blinder correction

The first 75,282-test checkpoint missed constructor WHNF: Show-based probes
forced the fields later and hid an exposed deferred carrier. That checkpoint is
not acceptance evidence for root strictness. Its complete 38-file source snapshot,
JAR, XML and hashes remain immutable in /tmp/kotdoc-rsa-before-strict-root.

The correction adds 224 original custom-DRG rows and 84 original effectful
callback rows. They demand only WHNF, either field, Show, ignored result, state,
repeated roots/fields, or state-root-state. Custom dictionary call counts expose
sharing and label byte/state failures by generation step. The original RSA
module remains unchanged. Kotlin checks repeated synchronous failure identity
as well as those independently observed call traces.

Both strict Integer fields now precede exposure of a generated Blinder. The
pure adapter keeps the computation inside the lazy mapped value, independently
of state; r precedes rm1/u demand. Callback generateBlinder is a strict-value
convenience. generateBlinderDeferred executes callback RNG effects now while
returning a memoized lazy source result; ignored IO results can therefore remain
unused. RSA.Types was audited: PublicKey and PrivateKey have no bang fields,
and remain lazy; Blinder is the only strict source constructor in this slice.

## Finite retry correction

The 75,599-test strict-root checkpoint still grew the JVM stack in both outer
key rejection and q==p retry. Its full46-file snapshot/JAR/XML/hashes remain
immutable in /tmp/kotdoc-rsa-before-retry-fix. Exact reviewer source/Java repros
and the additional100-attempt maxAction counterexample are under preliminary/.
The maxAction case exhausted a256KiB stack after91draws instead of reaching
the source generateMax100-attempt error.

The correction uses an RSA-local iterative key-generation computation under a
single memoized result. Each dictionary draw keeps its tuple root and successor
state lazy; the final state is not forced to expose a successful key value.
The large-range maxAction path reuses accepted iterative Number.Generate with
the same lazy dictionary bridge. Small-range lazy projections are preserved;
accepted dependencies and the generic monad implementation are unchanged.

Eighty-eight new rows execute original RSA with finite caller DRG budgets up to
5,000draws, eventual success after up to2,500retries, pair/byte/state failures,
ignored results and repeated value/state projections. Sixty more rows cover the
exact100-attempt max error, success after99rejections and dictionary failures.
All148rows run under a256KiB JVM stack. These are ordinary original RSA
computations with bounded caller errors, distinct from the earlier instrumented
e<=1loop observer. All older2,589+308expectations remain byte-exact.
