import Text.Pandoc.Builder (Many)
import Text.Read (readPrec)
import Text.ParserCombinators.ReadPrec (ReadPrec, readPrec_to_S)
import Control.Exception
main = mapM_ probe ["Many 1", "Many \"a", "Many 'a", "Many {unMany = fromList 1"]
 where probe s = do
        r <- try (evaluate (null (readPrec_to_S (readPrec :: ReadPrec (Many Int)) 0 (s ++ error "lex-tail")))) :: IO (Either SomeException Bool)
        print (s, either (takeWhile (/='\n') . displayException) show r)
