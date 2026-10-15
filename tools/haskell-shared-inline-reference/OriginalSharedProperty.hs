-- Exact property body from pinned Pandoc Tests.Shared; GPL-2.0-or-later.
module OriginalSharedProperty where
import Text.Pandoc.Definition (Inline)
import Text.Pandoc.Shared (stringifyInlines, stringify)
stringifyInlinesMatchesStringify :: [Inline] -> Bool
stringifyInlinesMatchesStringify ils =
  stringifyInlines ils == stringify ils
