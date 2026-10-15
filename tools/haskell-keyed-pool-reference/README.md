Data.KeyedPool is private in http-client 0.7.19. LiveKeyedPool.hs is the entire
pinned original module with only its module name changed, compiled with the
frozen GHC 9.12.2 plan and real original clocks, weak IORefs, STM and threads.
No algorithm or expectation is rewritten. The importer verifies every byte.

OriginalKeyedPool.hs has only its module name and three effect-import lines
changed. PoolClock supplies deterministic time, weak-finalizer registration
and reaper delay/fork boundaries. The entire algorithm body remains unchanged.
The observer can drive captured original finalizer actions and gated reaper
iterations without waiting 30 wall-clock seconds or relying on GC scheduling.
Both original source copies retain all comments and the complete project MIT
notice is preserved in licenses/http-client-MIT.txt and the distributable JAR.

The observer logs original create/destroy calls, results, reuse flags and errors.
Controlled runs also log clock reads, finalizer registrations, reaper startup
and exception handling. Public-operation runs use the original real effects.
Additional live cases explicitly forget handles/pools and wait for actual GHC
GC finalization; those cases must succeed before the corpus can be saved.
Stable roots retain the driver-owned handle map and pool reference until each
explicit forget, avoiding accidental lifetime optimization in the harness.
Each command result is serialized and detached immediately, so a lazy JSON
projection cannot accidentally retain the Managed wrapper during later GC.

Worker gates exercise resource creation outside STM, destruction outside STM,
concurrent duplicate release and async interruption of blocked creation or
overflow destruction. Other cases cover idle-only limits, LIFO reuse, first
insertion with nonpositive per-key limits, global capacity, exactly-once release,
null-valued resources, destructor errors, closed pools, reaper time boundaries,
rollback/prefix behavior, time errors and reaper handler failures.

No original KeyedPool test module exists in the pinned release. These are
independent original-code observations, not invented original unit-test counts.
The Kotlin suite also exercises actual JVM Cleaner reclamation with the real
runtime, including a cycle reachable only through the create callback.
Generic Haskell STM/GC/async masking, all possible Ord dictionary behavior and
platform runtime APIs are not declared wholly ported. Production defaults use
real time, a daemon reaper every five seconds, 30-second idle expiry and Cleaner.

Build here:

    cabal build keyed-pool-reference --project-file=cabal.project --builddir=/tmp/kotdoc-keyed-pool-build

Capture/check from the root:

    python3 tools/import-keyed-pool-fixtures.py --oracle /path/to/keyed-pool-reference
    python3 tools/import-keyed-pool-fixtures.py --check-source

No Haskell process is a production dependency. Manager/socket/TLS/proxy/history
integration and all remaining Pandoc/calibre work remain in docs/PORTING.md.
