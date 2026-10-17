# ChaChaDRG, pure Random.Types, Seed helpers and Probabilistic

The translated scope is the entire ChaChaDRG module (two initializers, private
positive/nonpositive generation, DRG/NFData instances), the pure Random.Types
state monad with Functor/Applicative/Monad/MonadRandom and withDRG, and the entire
Probabilistic derivation. Crypto.Random's Seed,40-byte constant and eight non-System
helpers are included. SystemDRG, getSystemDRG, actual OS entropy backends and the
MonadRandom IO instance remain unported dependencies. A host entropy callback is
explicit; no JDK RNG replaces ChaCha or pretends to implement upstream OS backends.
One CryptoProbabilistic instance represents one process-secret lifetime; the host
must retain that instance for that lifetime. Creating another instance models
another process, not another call in the same process.

ChaChaDRG uses the accepted StateSimple8-round rekey generator. Nonpositive counts
return empty and the original state, without demanding that state. This differs
from public generateSimple(0), which copies. initializeWords writes five Word64
values in native byte order. Seed Integer conversion uses positive modulo2^320
and exactly40 big-endian bytes, with the accepted source Integer serialization.
seedFromBinary copies and requires exactly40 bytes with SeedSizeInvalid. seedNew
requests40 bytes; INSECURE_ENTROPY requests64 and SHA512-hashes to40. The source conditional scrubbedHash512 helper
retains its explicit logical context clear; temporary digest bytes are cleared too. Neither branch
adds a check on a custom MonadRandom provider's actual response length. withRandomBytes
preserves source lazy output/state components, including ignored output mapping.

Kotlin's pseudo-random representation explicitly keeps pair components and mapped
arguments lazy. CryptoDrgOperations carries the source DRG dictionary separately
from the generator value, so a zero request does not force an undefined ChaCha
state. map/flatMap callbacks take Lazy arguments; apply takes a Lazy argument
function. Strict callbacks may explicitly demand value. Ordinary Kotlin constructors
remain eager; deferred generator/material adapters expose the tested source demand
boundaries instead of claiming general language evaluation equivalence.

## Independent oracle

All pinned implementation/test bytes are checked against the archive manifest.
A development Cabal overlay only exposes six hidden modules; it changes no library
implementation/test bytes. ObservedProbabilistic changes only the module header,
exposing the existing private process secret. The oracle draws that real secret
through the unmodified source entropy path, observes original source results and
checks them against a separately named ControlledProbabilistic. That controlled
copy mechanically adds a secret parameter and removes the private global binding;
all derivation expressions remain unchanged. The captured secret is dev-test data,
not a production secret. Final replay uses that retained secret, making independent
expectations deterministic without deriving any expected value from Kotlin.

InsecureRandom is the entire unchanged Crypto.Random module with only a module
rename and an explicit INSECURE_ENTROPY CPP define. It compiles and executes the
actual alternative seedNew body on this host. This is not an actual iOS ABI build.
The source matrix records every included family and excluded dependency. The
oracle exercises actual pure state-monad operations, captures requested draw lengths,
probes the old immutable DRG and final DRG, and uses200 actual Utils.TestDRG generators.
There is no dedicated DRG/Probabilistic original test module. Consumer tests in
NumberSpec/AFIS/ElGamal/Rabin remain individually pending until their Kotlin suites
execute; reading their source does not count as test completion.

## Limits and remaining work

This is actual GHC9.12.2 -O2 AArch64 little-endian observation. Native Word64 byte
order is checked here; big-endian/actual32-bit ABI execution is not claimed. Source
Int64 values beyond the Kotlin Int-sized byte-buffer domain and actual giant
allocations are not tested. ChaCha raw optimized backend,136-vs132 logical-context
allocation and physical memory/scrubbing/timing limits remain those of the accepted
core/public checkpoints. This module does not introduce negative counts into unsafe
public generateSimple: the source DRG guard returns before that call. Logical
ownership is tested, not universal physical erasure or concurrent exception choice.
Process-secret forcing/error order is demonstrated by finite sequential probes,
not a proof of all GHC bottom/async-exception or JVM concurrent behavior.

Prime's nine exports/420 small primes and actual probabilistic fallback remain next.
Other signatures/trust/chain/rest613, complete Pandoc623 and calibre624–626 stay open.
