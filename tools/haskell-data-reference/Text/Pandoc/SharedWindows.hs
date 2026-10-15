module Text.Pandoc.SharedWindows (makeCanonical, tshow, safeRead) where
import qualified Data.Text as T
import Control.Monad (MonadPlus,mzero)
import Data.Char (isSpace)
import System.FilePath.Windows (splitDirectories)
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

safeRead :: (MonadPlus m, Read a) => T.Text -> m a
safeRead = safeStrRead . T.unpack

safeStrRead :: (MonadPlus m, Read a) => String -> m a
safeStrRead s = case reads s of
                  (d,x):_
                    | all isSpace x -> return d
                  _                 -> mzero
