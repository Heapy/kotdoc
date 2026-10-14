-- Unchanged Shared.makeCanonical definition from pinned Pandoc.
-- Copyright (C) 2006-2026 John MacFarlane. GPL-2.0-or-later.
module CanonicalPosix where
import System.FilePath.Posix (splitDirectories)
import qualified System.FilePath.Posix as Posix
import qualified Data.List as L
makeCanonical :: FilePath -> FilePath
makeCanonical = Posix.joinPath . transformPathParts . splitDirectories
 where  transformPathParts = reverse . L.foldl' go []
        go as        "."  = as
        go ("..":as) ".." = ["..", ".."] <> as
        go (_:as)    ".." = as
        go as        x    = x : as
