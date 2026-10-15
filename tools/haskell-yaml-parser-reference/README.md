# Original libyaml parser reference

Compiles unchanged complete libyaml 0.2.5 api/reader/scanner/parser C modules.
The bridge provides inputs and observes actual events, parser state/return stacks,
marks, directives, peek availability and scanner state after each parse call.
No parser algorithm is copied into the observer. GHC is JSON transport only.
All native programs are test oracles, never production conversion dependencies.
