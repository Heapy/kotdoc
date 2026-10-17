# Complete Crypto.Number.Generate checkpoint

All four exports and both GenTopPolicy constructors/Show/Eq are ported from the
pinned module. Number.Basic and Number.Serialize are accepted dependencies.
The source API operates on arbitrary-precision Integer, so Kotlin BigInteger is
its representation here; this does not replace any specialized word algorithm.
Production takes an explicit random-byte provider, corresponding to MonadRandom.
No upstream executable, Java probable-prime implementation or foreign engine
is a production dependency.

## Independent source evidence

The ordinary oracle calls the unmodified pinned Crypto.Number.Generate through
original crypton2.0.1/ram0.22.1. It records exact random request lengths, returned
bytes, numeric/error results and snapshots of the retained provider buffers.
An existential ByteArrayAccess wrapper retains the actual returned arrays,
so snapshot equality checks source copy ownership, not a second detached copy.
The37127-row corpus covers all first-byte values at bit widths1..17, larger
boundaries through4096, prefix-vs-suffix selection, odd/top policies, simple /
normal / overgeneration range paths, every small candidate for127/128/129/159,
all three overgeneration acceptance branches, final attempt100/exhaustion,
provider exceptions, nonpositive sizes/ranges, inclusive unsorted bounds,
policy instances and finite demand/effect ordering.

The exact original NumberSpec four property families execute at200 trials each.
A mechanically selected copy keeps their original expressions and all input
classes unchanged. Another800 recorded samples use the actual TestDRG,
Int1_2901 and Positive Integer Arbitrary instances. Their traced ChaChaDRG
results are compared against original withTestDRG, then replayed in Kotlin using
those externally recorded random bytes. The original four property expressions
are also translated and checked on these800 Kotlin results. This is not a port
of ChaChaDRG, system entropy or all NumberSpec/Prime tests.

## Exact behavior and safety boundary

- bits<=0 returns0 without RNG calls or demanding top/odd thunks.
- generateParams masks the unused HIGH bits and preserves the suffix;
  generatePrefix retains the requested most significant prefix instead.
- Top tweaks, masking and odd forcing retain original ordering and copy input.
- range<=1 returns0; range<127 intentionally uses biased modulo. No uniformity
  improvement or added rejection is made.
- Normal and overgeneration paths have exactly100 attempts and original error
  text. The latter accepts r, r-range or r-2range in that order, then retries.
- generateBetween is inclusive and does not reject reversed endpoints: a
  nonpositive resulting range returns low without RNG calls.
- Valid random providers return exactly the requested length. Malformed provider
  lengths are outside the source contract and rejected by the Kotlin bridge.

The original SetTwoHighest tweak for bits=1 unconditionally writes p1 beyond
its one-byte LOGICAL copy. Ordinary native numerical observations (512 rows)
are retained unchanged, with result1. Kotlin safely returns that observed numeric
prefix and does not perform the out-of-bounds store. Memory parity is expressly
NOT claimed for this source case.

A SEPARATE dev-only ObservedNumberGenerate copy substitutes a guarded copy
allocator and exposes before/after sentinels; it also replaces hidden imports
with equivalent public Data.ByteArray/Word8 imports. The exact tweak body stays
unchanged. Extractor --check verifies every transformation against pinned bytes.
The120 guarded probes compare their numeric result with the ordinary oracle;
eight bits=1/SetTwoHighest probes change the trailing logical sentinel34 to162.
The other112 retain it. This demonstrates the source pointer store under the
controlled allocation, not ordinary GHC heap layout, allocator padding or GC
memory effects. It is not relabeled as an unmodified oracle.

Explicit Lazy bridges model finite observed demand ordering (including high
before low for the bounds expression). They do not prove universal GHC bottom
or exception precedence. Kotlin ordinary calls remain eager. Virtual near-Int
boundaries use a provider that throws after recording the requested length;
no huge allocation/hashing/generation is claimed. Kotlin bits are Int-sized;
actual Haskell Int is64-bit on the pinned host. Unbounded memory, physical
scrubbing, JVM timing and other OS/ABI behavior are not proven.

## Remaining full scope

SourceMatrix retains the open matrix: Prime's nine exports and probabilistic /
DRG dependencies; RSA/DSA/ECDSA/Ed25519/EdDSA/Rabin/ElGamal and required hash /
padding operations; X509 validation/signature/cache/trust/chain. Chain/store
representation codecs are not signature or trust validation. Other cryptographic
operations and the full local:613 scope remain open. Pandoc623 all formats/tests,
calibre624 MOBI6/KF8/oldKindle,625 other formats/tests and626 final delivery remain
required. This checkpoint does not close any of those broader goals.

## Reproduce

- python3 tools/extract-number-generate-reference.py --check
- python3 tools/import-number-generate-fixtures.py --check-source
- python3 tools/import-number-generate-fixtures.py --oracle <executable> --verify-oracle
- :core:test --tests io.heapy.kotdoc.crypto.CryptoNumberGenerateTest :core:jar :cli:installDist

The dev executable is /tmp/kotdoc-ed448-haskell-build/build/aarch64-osx/ghc-9.12.2/kotdoc-number-generate-reference-0.1.0.0/x/number-generate-reference/opt/build/number-generate-reference/number-generate-reference.
Original crypton/RAM BSD notices are retained. Original implementation/test bytes
and numeric expectations are independent of the Kotlin implementation.
