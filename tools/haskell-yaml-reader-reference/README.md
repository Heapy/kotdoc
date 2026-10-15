# Native YAML reader reference

Compiles unchanged libyaml 0.2.5 api.c and reader.c retained in vendor/.
ReaderBridge only provides input chunks, advances the consumed pointer, and
observes actual native buffers/error/encoding state. No decoder is duplicated.
The Haskell program transports JSON requests/results; frozen GHC 9.12.2 plan.
Native code is only a test oracle, never a production conversion dependency.
