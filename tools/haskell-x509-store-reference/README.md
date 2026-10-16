# Original X509 store reference

Use GHC 9.12.2 and the retained Cabal freeze. The observer compiles the pinned
Data.X509.Memory, File and CertificateStore modules directly from vendor;
crypton 2.0.1 is the same local source package as the preceding X509 references.
No Haskell program is a production kotdoc dependency.

From this directory:

```sh
cabal build --offline x509-store-reference original-store-tests
cabal run --offline original-store-tests
cabal list-bin x509-store-reference
```

From the repository root, pass the last command's absolute executable path:

```sh
python3 tools/import-x509-store-fixtures.py --oracle /absolute/path/x509-store-reference
python3 tools/import-x509-store-fixtures.py --verify-oracle --oracle /absolute/path/x509-store-reference
python3 tools/import-x509-store-fixtures.py --check-source
```

OriginalStoreFixtures.hs retains the unchanged fixture declarations before
memoryKeyTests from Tests/Tests.hs, changing only the module name/export list.
The importer verifies this extraction. The original-store-tests executable
uses the complete original Tests/Tests.hs unchanged and runs its 32 assertions.
The observer's --vectors replays those assertions independently; JSON-lines
requests additionally exercise real temporary files and lazy maps/composites.
Temporary file error messages normalize only missing-file/directory path noise.
Map source is retained separately to document strict keys and balancing; the
observer uses the freeze-pinned containers 0.7 implementation supplied by GHC.

The Kotlin corpus includes all eight certificate and private-key families,
all key label combinations, every original DER truncation and byte mutation,
multi-PEM ordering, returned versus thrown failures, subject duplicate policies,
empty/composite behavior, partial DN and certificate payloads, and filesystem
filtering. It does not claim native CA loading, trust validation, independent
other-platform reference captures, concurrent filesystem behavior or exact
upstream lazy handle finalization.
