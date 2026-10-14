module Text.Pandoc.Shared (makeCanonical, tshow) where
import qualified Data.Text as T
import System.FilePath (splitDirectories)
import qualified System.FilePath.Posix as Posix
import qualified Data.List as L

makeCanonical :: FilePath -> FilePath
makeCanonical = Posix.joinPath . transformPathParts . splitDirectories
 where  transformPathParts = reverse . L.foldl' go []
        go as        "."  = as
        go ("..":as) ".." = ["..", ".."] <> as
        go (_:as)    ".." = as
        go as        x    = x : as


tshow :: Show a => a -> T.Text
tshow = T.pack . show
