{-# LANGUAGE ForeignFunctionInterface #-}
module Main where
import qualified OriginalPosixEnvironment as P
import qualified OriginalWindowsEnvironment as W
import EnvironmentEffects
import qualified System.Environment as E
import GHC.Internal.IO.Encoding (mkTextEncoding,setFileSystemEncoding)
import qualified GHC.Internal.Foreign.C.String.Encoding as Enc
import Foreign
import Data.IORef
import Data.Char
import Data.Word
import Control.Exception
import Control.Monad
import System.IO
import System.Info (fullCompilerVersion)
import Data.Version (showVersion)
import Foreign.C.String (peekCAString)
import Foreign.C.Types (CChar)

data Command = Decode String [[Int]] | Posix String (Maybe [[Int]]) | Windows (Maybe [Int])
             | Live String (Maybe [[Int]]) | Version | Locale deriving Read
foreign import ccall unsafe "kotdoc_environ_address" environmentAddress :: IO (Ptr (Ptr (Ptr Word8)))
foreign import ccall unsafe "kotdoc_locale_name" localeName :: IO (Ptr CChar)
answer :: [[[Int]]] -> IO String
answer value = let s=show value in evaluate (length s) >> pure s
view :: [(String,String)] -> [[[Int]]]
view = map (\(a,b) -> [map ord a,map ord b])
withRecords :: Maybe [[Int]] -> (Ptr (Ptr Word8) -> IO a) -> IO a
withRecords Nothing action = action nullPtr
withRecords (Just records) action = withMany (withArray0 0 . map fromIntegral) records $ \ptrs -> withArray0 nullPtr ptrs action
run :: Command -> IO String
run Version = answer [[[ord c | c<-showVersion fullCompilerVersion]]]
run Locale = localeName >>= peekCAString >>= \s -> answer [[map ord s]]
run (Decode name batches) = do
 enc<-mkTextEncoding (name++"//ROUNDTRIP")
 strings<-forM batches $ \bs -> withArrayLen (map fromIntegral bs :: [Word8]) $ \n p -> Enc.peekCStringLen enc (castPtr p,n)
 answer [[map ord s] | s<-strings]
run (Posix name records) = withRecords records $ \ptr -> do
 writeIORef encodingName name
 writeIORef block (castPtr ptr)
 P.getEnvironment >>= answer . view
run (Windows units) = case units of
 Nothing -> writeIORef wideBlock nullPtr >> W.getEnvironment >>= answer . view
 Just xs -> withArray (map fromIntegral xs++replicate 16 0 :: [Word16]) $ \ptr -> do
   writeIORef wideBlock ptr
   W.getEnvironment >>= answer . view
run (Live name records) = withRecords records $ \ptr -> do
 enc<-mkTextEncoding (name++"//ROUNDTRIP");setFileSystemEncoding enc
 address<-environmentAddress
 bracket (peek address) (poke address) $ \_ -> do
   poke address ptr
   E.getEnvironment >>= answer . view
main :: IO ()
main = do
 hSetBuffering stdout LineBuffering
 getContents >>= mapM_ (\line -> do
   result<-try $ run (read line)
   putStrLn $ either (\(_::IOException) -> "{\"error\":\"io\"}") id result) . lines
