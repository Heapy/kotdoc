# System.X509 reference

GHC 9.12.2, the retained freeze, and local crypton 2.0.1 are required. From this
directory, `cabal build --offline x509-system-reference native-system-reference`
builds the controlled observer and a real OS store snapshot program.
`cabal list-bin x509-system-reference` prints the observer path. From the root:

```sh
python3 tools/import-x509-system-fixtures.py --oracle /absolute/path/x509-system-reference
python3 tools/import-x509-system-fixtures.py --verify-oracle --oracle /absolute/path/x509-system-reference
python3 tools/import-x509-system-fixtures.py --check-source
```

The importer mechanically verifies the controlled copies against the pinned
crypton-x509-system 1.9.0 archive retained in http-tls-sources.json. The only
changes redirect CertificateStore I/O and replace the three Windows FFI
declarations with controlled IO functions. Home System.Environment and
System.Process modules provide observable environment/process effects.
System.Win32.Types supplies the pointer/DWORD/string types needed by the
unchanged Windows context decoder; the ROOT string is ASCII. All policy,
enumeration, catch placement, native pointer peeks, byte copies, ASN1 decoding,
store construction, environment precedence and process-wait code is original.
Allocated contexts have the actual host pointer alignment. The reference runs
on POSIX; this is not an independent Windows ABI capture. Kotlin separately
tests JNA layout/copying and executes actual Crypt32 in Windows CI.

JSON-lines requests configure variables, read results, stdout bytes, context
encoding types/bytes, process exit codes and one-shot errors by effect/call
number. Results include the complete effect trace and either the selected
observation or its exception. Cases distinguish ignored results, emptiness,
count and encoded certificate lists. Source read results may deliberately
contain lazy failing Maybe/store/certificate values to test demand order;
these controlled values are not claimed to model physical filesystem contents.
Cleanup closes observer-owned fake stdout handles after observing results.

The native executable compiles directly against the retained upstream modules
with real environment, filesystem and process APIs. On macOS it uses the
original security command; on Linux it selects Unix paths. Compare its count
and SHA256 of concatenated, subject-ordered signed bytes against the Kotlin
child's snapshot with SSL_CERT_FILE, SSL_CERT_DIR and SYSTEM_CERTIFICATE_PATH
removed in both child environments. Machine CA bytes are not fixture data.
The package defines no original test suite; the corpus is new independent
observation coverage, not a claim of upstream tests that do not exist.
