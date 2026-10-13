# Original Haskell test references

These adapters compile retained upstream Haskell modules unchanged. They are
used only to capture independent Kotlin test expectations. Neither the Kotlin
runtime nor routine CI tests require GHC or an external converter.

`BitStream.hs` exercises `Codec.Picture.BitWriter` from JuicyPixels 3.3.9,
including reads, state save/restore, JPEG byte alignment/stuffing, GIF output,
repeated finalization and writes that cross the original 256 KiB buffer boundary.
It does not reimplement the algorithms. Input is one Haskell `Read` tuple per
line; output is a JSON-compatible numeric list per line.

Build from this directory with GHC 9.12.2 and cabal-install 3.14.2.0:

```sh
cabal build bitstream-reference
cabal list-bin bitstream-reference
```

Pass the executable path printed by the second command to the importer, from
the repository root:

```sh
python3 tools/import-image-bitstream-fixtures.py --oracle /path/to/bitstream-reference
python3 tools/import-image-bitstream-fixtures.py --check-source
```

`cabal.project.freeze` pins the compiler's boot libraries and vector/primitive
versions. Sources are resolved relative to this repository, so this development
package is not intended for `cabal sdist` distribution. Keep compilation output
outside the source tree with `--builddir=/path/to/build` if desired.

The initial reference environment used official aarch64 macOS binary archives:

- GHC 9.12.2: https://downloads.haskell.org/~ghc/9.12.2/ghc-9.12.2-aarch64-apple-darwin.tar.xz
  SHA-256 `4b61b933028c63ace950236ea3382d02e51a3d9cbd1ca3f6cf4fe14c71ff436c`.
- cabal-install 3.14.2.0: https://downloads.haskell.org/~cabal/cabal-install-3.14.2.0/cabal-install-3.14.2.0-aarch64-darwin.tar.xz
  SHA-256 `c599c888c4c72731a2abbbab4c8443f9e604d511d947793864a4e9d7f9dfff83`.

Both archives were checked against the corresponding official SHA256SUMS.
The tools/cache/build outputs live outside this repository. The original
JuicyPixels BSD-3-Clause notices are retained under vendor/ and licenses/.
