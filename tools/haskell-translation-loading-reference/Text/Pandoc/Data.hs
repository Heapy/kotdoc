module Text.Pandoc.Data (readDataFile) where
import Text.Pandoc.Class
import qualified Data.ByteString as B
-- File routing itself is not replaced or claimed ported by this boundary adapter.
readDataFile :: PandocMonad m => FilePath -> m B.ByteString
readDataFile = readDataFileEffect
