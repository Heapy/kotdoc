# Independent complete Text.Pandoc.Data algorithm oracle

Compiles all original Data function bodies unchanged in embedded/installed and
POSIX/Windows builds. Adapters only change module/import names and CPP selection.
The original BakedIn module runs its real Template Haskell embedding against
symlinks to the pinned MANUAL/data tree. The ZIP source is copied unchanged from
the protected codec oracle. PandocMonad state/report/checkUserDataDir/timestamp/
UTF8 definitions and Shared.makeCanonical/safeRead are exact source projections.

Only primitive file/environment/clock/directory effects are supplied by the
observing runtime; complete routing/archive algorithms remain original. Directory
IO calls retain their IO exception boundary. Records include every effect and
state write, exact output SHA256/length and rational timestamps. Actual embedded
catalog bytes are exported for byte-exact Kotlin resources, without invoking
Kotlin. Other PandocMonad/OS implementation details are not replaced or claimed
complete by the effect hooks.
