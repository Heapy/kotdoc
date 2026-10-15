module Main where
import qualified OriginalNumeric as P
import qualified Data.Text as T
import Data.Char (chr)
import Data.Word (Word64)
import GHC.Float (castWord64ToDouble)
import Numeric (showEFloat,showFFloat)
import System.IO
import Control.Monad (forM_)
main = do
 hSetBuffering stdout LineBuffering
 contents <- getContents
 forM_ (lines contents) $ \line -> do
  let (op,nums) = read line :: (String,[Integer])
      expr = T.pack (map (chr . fromInteger) nums)
      value = castWord64ToDouble (fromInteger (head nums) :: Word64)
      result = case op of
       "integer" -> fmap T.unpack (P.evalInteval expr)
       "float" -> fmap T.unpack (P.evalFpeval expr)
       "format" -> Just (T.unpack (P.formatFp value))
       "fixed" -> Just (showFFloat Nothing value "")
       "exponent" -> Just (showEFloat (Just(fromInteger(nums!!1))) value "")
       _ -> error "request"
  putStrLn (maybe "null" show result)
