-- GHC 9.12.2 / base 4.21.0.0 Data.Char, Unicode 16.0.0.
-- Independent classifier data for the original Shared.textToIdentifier rules.
import Data.Char
import Data.Bits
import System.Environment (getArgs)
main = do
  args <- getArgs
  if args == ["--expanded"] then putStr $ map (chr . (65+)) flags else putStr $ unlines [show n ++ "\t" ++ show f | (n,f,prev) <- zip3 [0..0x10ffff] flags (-1:flags), f /= prev]
  where
    flags = map (classify . chr) [0..0x10ffff]
    classify c = (if isAlpha c then 1 else 0) .|. (if isAlphaNum c then 2 else 0) .|. (if isSpace c then 4 else 0) .|. (if generalCategory c `elem` [NonSpacingMark,SpacingCombiningMark,EnclosingMark,ConnectorPunctuation] then 8 else 0) :: Int
