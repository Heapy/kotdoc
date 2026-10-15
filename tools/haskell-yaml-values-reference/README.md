# Independent yaml value decoder oracle

Runs unchanged yaml 0.11.11.2 decodeHelper_/decodeAllHelper_, Text.Libyaml's
actual byte decoder, and all original prettyPrintParseException branches under
the frozen dependency plan. The transport supplies byte inputs or direct events,
observes demanded event counts, projects exact Scientific fields/ordered objects,
and records warning paths plus raw String character output. Direct malformed
event streams exercise higher-level parser errors unreachable from valid syntax.

ProbeException supplies a boundary Show string for the generic SomeException
branch; it does not emulate platform IO exceptions. No Kotlin code is called.
`literal` uses GHC Read String then ByteString.Char8 packing for original tests.
