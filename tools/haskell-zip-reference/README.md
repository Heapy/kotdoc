# Independent zip-archive 0.5 oracle

Compiles the full retained original Codec.Archive.Zip source byte-for-byte.
The Windows build changes only module name, FilePath import and the source's
_WINDOWS CPP selector; all algorithm bodies remain original. IO functions are
compiled but pure codec/record/entry observations do not claim their behavior.
Transport forces JSON bytes inside the exception catcher and never calls Kotlin.

The oracle profile is GHC 9.12.2 on aarch64 macOS with clang CPP. The source's
CP437 string-gap continuations are spliced by that preprocessor, introducing
literal continuation spaces/backslashes/digits into the runtime table. The exact
preprocessed declaration is retained in cp437-clang-cpp.txt and independently
checked against the original source's line-splicing transformation. Filename
observations deliberately preserve this pinned behavior; they do not assert
standard CP437 mapping or unverified other-preprocessor equivalence.
