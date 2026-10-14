# Common state reference

Compiles the complete unchanged pinned CommonState (including PANDOC_HTTP_SUPPORT),
Logging, MediaBag, MIME, URI, UTF8, Error, Translations.Types and Lang modules.
PandocMonad.hs contains only byte-exact selected state/logging definitions; the
whole application/IO/Lua class is not stubbed as a claimed full implementation.
Shared and Citeproc contain the exact required previously retained definitions.
MessageTransport constructs values only. Expected results execute native code.
GHC 9.12.2, frozen Cabal plan; no oracle is a production dependency.
