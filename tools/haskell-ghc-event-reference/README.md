# GHC event-manager reference

Development-only observer; production invokes Kotlin and native OS calls, not
this program. Requires the complete GHC version 9.12.2 and retained exact freeze.

Twelve original GHC Event modules compile locally against that pinned compiler.
Only Manager's export list changes to expose records, state and onFdEvent for
observation. Every implementation byte is original, including backend and control
algorithms. OriginalThreadWait retains the complete original threadWait and
closeFdWith declarations/bodies verbatim. Its header supplies the manager/array
lookup boundary, allowing one or two explicit managers without changing either
algorithm. The importer verifies both forms of extraction against the retained
22-file original Event source snapshot and its per-file hashes.

Controlled programs execute original registration, rollback, callback partition,
rearming, close, step, loop, release, shutdown and cleanup. Their fake backend
records calls and returns scripted True/False/IO failures; callbacks may fail.
Native programs use the original compiled EPoll/KQueue backend for failed
registrations. Complete per-action table snapshots retain absent versus empty
entries, individual event/lifetime values and callback ordering. Unique values,
control-channel descriptor numbers and unrelated scheduler order are not projected.

Native pipe scenarios execute the unmodified original threadWait/closeFdWith,
with gates waiting for actual callback-table registrations. They observe readiness,
partial/full cancellation followed by replacement, one raw close across one/two
managers, and callback notification even if the raw-close action throws. Async
results identify ThreadKilled; Kotlin projects Java interruption at that blocked
wait boundary to the same cancellation outcome, without claiming the whole GHC
async-exception runtime. Other native scenarios release/resume/shut down real
poll loops and make up to 90 pipe descriptors ready before stepping, observing
native array growth across the original initial 64-event capacity.

Darwin and Linux/glibc are captured separately. Header layouts/constants include
kevent/epoll_event/timespec, original one-shot policy and errno/fcntl values.
No expected registry, callback, wait or backend algorithm is rewritten.

Build outside the source tree:

```
cabal build exe:ghc-event-reference --offline --builddir=/tmp/kotdoc-ghc-event-build
cabal list-bin exe:ghc-event-reference --builddir=/tmp/kotdoc-ghc-event-build
```

Capture with `tools/import-ghc-event-fixtures.py --platform macos-64`
(or linux-64) `--oracle /path/to/executable`. Streaming wrappers may run the
same pinned original code in a Linux container. `--check-source` checks all
source hashes, extraction, observations and ABI assets. Expectations never come
from Kotlin. These are observations, not a claim to have translated the entire
GHC testsuite or to have implemented the complete runtime.

Remaining runtime work includes capability-array replacement/retry, full GHC
masking/throwTo/signal/timer/weak-reference behavior, all exceptional backend-close
paths, standalone-manager GC ownership, Windows and other native backends/ABIs.
The entire Pandoc/all-tests and then calibre MOBI/other-formats/all-tests goal
remains required.
