module Text.Pandoc.ClassWindows (module C,checkUserDataDir) where
import Text.Pandoc.Class as C hiding (checkUserDataDir,isRelativeToParentDir)
import Text.Pandoc.Class.CommonState
import Text.Pandoc.SharedWindows (makeCanonical)
import System.FilePath.Windows (isRelative,splitDirectories)
isRelativeToParentDir :: FilePath -> Bool
isRelativeToParentDir fname =
  case splitDirectories (makeCanonical fname) of
    "..":_ -> True
    _      -> False

-- | Returns possible user data directory if the file path refers to a file or
-- subdirectory within it.
checkUserDataDir :: PandocMonad m => FilePath -> m (Maybe FilePath)
checkUserDataDir fname =
  if isRelative fname && not (isRelativeToParentDir fname)
     then getUserDataDir
     else return Nothing

