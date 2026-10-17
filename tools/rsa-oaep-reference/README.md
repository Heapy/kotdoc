# MGF1 and RSA OAEP source checkpoint

Production Kotlin ports both complete pinned modules. MaskGenFunction exports
one function, mgf1, and the MaskGenAlgorithm function alias; hashCounter is its
local helper. OAEP exports Params (constructor and three selectors) and five
functions: defaultOAEPParams, encryptWithSeed, encrypt, decrypt, decryptSafer.
Its private unpad has step/select full-scan helpers. These are six OAEP top-level
functions, not six public functions. Source Internal.and'/&&! behavior is inlined
as three strict Boolean comparisons. Error names map the five original RSA.Error
constructors; full RSA.Types reflection/Read/Generic is a separate gate.

Sixteen existing hash adapters, generic raw HashAlgorithm and generic seed
ByteArrayAccess reuse accepted engines; no JDK RSA/OAEP/MGF/hash or upstream
runtime is a production dependency. Public concrete encrypt inputs are copied;
explicit deferred/borrowed providers retain their stated caller-owned boundaries.
Success stores a memoized lazy encoded/cipher result, so forcing a Right root
is distinct from forcing its bytes. bytes() returns a copy. Params can retain
explicit lazy label providers; the concrete label constructor owns a copy.

The independent GHC9.12.2-O2 probes exposed important ordering details: MGF
length and seed demand precede hash initialization; empty seeds skip the pointer
callback but still initialize the strict seeded context. RSA public modulus and
exponent are demanded before the encoded message in this selected OAEP caller.
The expected label hash is demanded before private decoding. Empty zipWith
inputs do not demand unused MGF callbacks. These observations are explicit
selected compiler profiles, not universal ordering of imprecise exceptions.
Raw IO Unit return roots are ignored, preserving callback effects.

Pure actions preserve separate value/state projections, ignored results,
custom pair-root/field faults and once-only synchronous sharing. IO callbacks
execute effects before returning a deferred Either. The safer IO bridge forces
prior state tokens to sequence callback effects, while byte results and the
strict Blinder value remain separately deferred. It reuses the accepted
CryptoRsa.generateBlinderAction byte-identically, without generic monad changes.
The original 100-retry error and success-after-99-retries paths are tested at
256 KiB stack for pure and IO consumers, including ignored/root/state views.
Strict host ByteArray callbacks are convenience adapters; explicit Io providers
represent returned lazy ByteString roots.

## Evidence and reproduction

`sh tools/rsa-oaep-reference/BuildHaskell.sh ordinary` builds the ordinary oracle;
`demand`, `random`, `callback`, `access` select the four other programs. The
package overlay has 437 byte-identical upstream files and only seven visibility
exports added to Cabal; pinned implementation and tests are never rewritten.
OriginalOaepSpec changes only its module export header. Its full suite has 23
examples: seven vectors encrypted/decrypted, six reject checks and three range
checks. The last 23 ordinary rows translate those exact source cases, including
encoded-block corruption/re-encryption and c+n, not just approximate roundtrips.

Run each script with --verify and its corresponding executable:

- capture.py: /tmp/kotdoc-oaep-haskell-oracle, 8157 ordinary rows and full23 suite.
- capture-demand.py: /tmp/kotdoc-oaep-demand-oracle, 1104 generic/error/root rows.
- capture-demand-extra.py: same executable, 720 paired-bottom observations.
- capture-random.py: /tmp/kotdoc-oaep-random-oracle, 480 pure state/RNG rows.
- capture-random-extra.py: same executable, 16 bounded retry observations.
- capture-callback.py: /tmp/kotdoc-oaep-callback-oracle, 288 IO callback rows.
- capture-callback-extra.py: same executable, 12 bounded retry observations.
- capture-access.py: /tmp/kotdoc-mgf-access-oracle, 192 generic seed-access rows.
- capture-boundary.py: ordinary executable, 130 virtual arithmetic controls.

`python3 tools/extract-rsa-oaep-reference.py --check` checks verbatim sources and
the whole original test body. `python3 tools/import-rsa-oaep-fixtures.py --check`
checks nine packaged corpora, current production/test and accepted dependency
hashes. Expectations are captured from original programs, never Kotlin.
The virtual observer evaluates the source divMod/maxCounter and original
four-byte Integer serialization separately; it is not a giant MGF allocation.

## Limits and remaining scope

Valid callback buffer sizes, nonnegative raw hash allocation dimensions and
non-retained pointers are required. Concrete array-backed lengths fit JVM Int;
OAEP key sizes retain the existing source Long record but allocating results
outside the JVM array domain is not claimed equivalent to a native giant heap.
The 130 virtual controls cover signed Int boundaries, division/remainders and
counter bytes, not an actual 2 GiB allocation/hash. Source portable hash/backend,
unspecified native context-tail, ABI/endianness boundaries remain inherited.
Source full scans and XOR/strict comparisons are functional evidence, not a JVM
constant-time guarantee. Arbitrary foreign mutation, physical scrubbing, async
exceptions, general Haskell bottoms and arbitrary custom ByteArray allocators
are not proved. Selected synchronous callback/error traces are preserved.

PKCS15/PSS, full RSA.Types, other signatures, SystemDRG/OS entropy, optimized
hash backends/missing hash families, Digest Data, X509 trust/chain and rest613
remain open. Full Pandoc623 and later calibre624–626 are not completed here.
