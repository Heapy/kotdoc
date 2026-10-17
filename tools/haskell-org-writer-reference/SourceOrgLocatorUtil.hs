-- Text.Pandoc.Citeproc.Util.splitStrWhen; GPL-2.0-or-later.
-- Copyright (C) John MacFarlane; complete pinned source retained in upstream.
module SourceOrgLocatorUtil(splitStrWhen)where
import Text.Pandoc.Definition
import qualified Data.Text as T
splitStrWhen :: (Char -> Bool) -> [Inline] -> [Inline]
splitStrWhen p = foldr go []
 where
  go (Str t) = (map Str (T.groupBy goesTogether t) ++)
  go x = (x :)
  goesTogether c d   = not (p c || p d)
