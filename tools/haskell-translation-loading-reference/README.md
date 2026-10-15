# Independent Pandoc translation loading oracle

Compiles the complete unchanged pinned Text.Pandoc.Translations module and its
Types/CommonState/Logging/Error/UTF8/Lang dependencies. PandocMonad's selected
state/report/toTextM/findDecodingError definitions are byte-exact projections.
The readDataFile import is an observing effect boundary: supplied bytes or
errors replace file routing, not translation selection/loading/cache logic.
File routing, other PandocMonad methods and platform IO are not claimed tested
by this projection. Frozen dependencies and original source bytes are retained.

Records each file read, state write and log callback, selected common state,
all term lookups and exact table Show/errors. File effects and log callbacks may
mutate/re-enter state or raise typed/host errors. No Kotlin code is invoked.
