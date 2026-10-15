module Text.Pandoc.Data (readDataFile) where
import Text.Pandoc.Class
import qualified Data.ByteString as B
-- Dispatch into the selected original Data build through the observing runtime.
readDataFile :: PandocMonad m=>FilePath->m B.ByteString
readDataFile=readDataFileEffect
