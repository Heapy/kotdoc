# Complete RSA.PKCS15 source checkpoint

All nine exports and private nonzero-random retry, makeSignature/addDigestPrefix,
full unpad scan/select are translated. HashAlgorithmASN1 covers all fourteen source
instances with actual accepted Kotlin MD2, MD5, SHA1/SHA2/SHA3 and RIPEMD160 engines.
No Prefix instance is invented. SourceMatrix.json lists the precise mapping.
RIPEMD160 and Number.Generate actual-storage corrections are independently accepted.

Independent corpora remain separate: ordinary5347, sourcePKCS15 examples31,
paired demand784, generic hash/ASN1 dictionaries416, defined pure RNG452,
IO callbacks372, root-only virtual bounds88. The unchanged original RSASpec37 run
supports31 PKCS15 and6 already accepted rawRSA examples; it does not double-count
those six as new PKCS15 tests. Original expected data is never derived from Kotlin.

RandomObservations.json preserves every original456 row, including four historical
empty-buffer safer/state observations. DefinedRandomObservations.json selects452
by exact original index. Only empty signSafer/decryptSafer state projections are
excluded: their Generate mask accesses p0 of zero-byte storage. No such unsafe
native operation is rerun. The eight short/long defined differences in the old
RandomDifferences.json now match the independently accepted Generate correction.
Neither old file nor old5347/31/784/416 expectations were rewritten.

Pure State can return Right-root without drawing RNG; IO executes its effects
before returning even if its result is ignored. Payload/state/error projections
share memoized computations. Source selected GHC-O2 demands the key record root
before hash/input while size fields remain lazy. Both earlier failed isolated
baselines remain under history/. Bounded5000 zero-byte retry and eventual-success
cases test stack behavior and effect order; they are not termination proofs.

The source input/output Bytes are immutable. The public Kotlin ByteArray adapter
snapshots inputs and returns copied result bytes. In particular unpad snapshots
its input before retaining a deferred Right payload. This host ownership adapter
is distinct from unsafe mutation through original immutable ByteArray pointers;
no universal native identity, heap erasure, constant-time or bottom claim follows.
The unpad loop scans every available octet, but JVM timing is not proven.

Virtual bounds include signed64-bit guard wrap and Right-root without evaluating
large payloads, using actual original functions. They allocate only small messages
and no giant result/RNG buffer. Actual Kotlin ByteArray/callback count is Int-sized;
materializing a source result beyond that host domain safely fails, not a claim of
large-memory parity. Word32 unpad scan matches valid Int-sized buffers. Generic
nonlawful dictionaries are finite compiler-profile observations, not universal
Haskell evaluation equivalence. Safer native storage domains are classified narrowly.

Reproduction: BuildHaskell.sh builds ordinary source oracle; capture.py,
capture-demand.py, capture-dictionary.py and capture-callback.py generate/verify
independent fixtures. capture-random.py now refuses to execute its historical unsafe4 and verifies
only the defined452 against the unchanged original456; it never rewrites them. The boundary original observer
calls public functions at root only. import-pkcs15-fixtures.py --check verifies
source copies, all14 verbatim prefixes, exact defined selection, current pins and
verbatim gz payloads. CurrentProfile.json distinguishes the new accepted Generate
binding from IntermediateProfile.json's historical baseline. Exact final commands,
XML/JAR/notices/class identity are saved in the final handoff evidence directory.

PKCS15 is not accepted until independent review. PSS/fullRSA.Types/SystemDRG,
other signatures/trust/chain/rest613 and full Pandoc/calibre remain OPEN.
