{-# LANGUAGE OverloadedStrings,ScopedTypeVariables,CPP #-}
#if __GLASGOW_HASKELL__ != 912 || __GLASGOW_HASKELL_PATCHLEVEL1__ != 2 || __GLASGOW_HASKELL_PATCHLEVEL2__ != 0
#error GHC 9.12.2 required
#endif
module Main where
import LookupEffects
import qualified OriginalPosixGetEnv as P
import qualified OriginalWindowsGetEnv as W
import qualified OriginalWindowsErrors as E
import qualified OriginalWindowsErrno as EN
import Data.Aeson
import Data.Char
import Data.IORef
import qualified Data.ByteString.Lazy.Char8 as L
import Foreign
import Foreign.C.Types (CInt)
import Control.Exception
import Control.Monad
import GHC.IO.Exception (IOException(..))
import System.IO

failure (e::SomeException)=case fromException e of
 Just (IOError _ typ loc desc errno filename)->object ["error" .= ("io"::String),"type" .= show typ,"location" .= map ord loc,"description" .= map ord desc,"errno" .= fmap (fromIntegral :: CInt -> Int) errno,"filename" .= fmap (map ord) filename]
 Nothing->case fromException e of
  Just (AssertionFailed msg)->object ["error" .= ("fatal"::String),"message" .= msg]
  _->object ["error" .= ("unexpected"::String),"message" .= displayException e]
withPtr Nothing action=action nullPtr
withPtr (Just xs) action=withArray0 0 xs action
run v=withPtr (fmap (map fromIntegral) (optional v "value"::Maybe [Int])::Maybe [Word8]) $ \ptr->
 withPtr (fmap (map fromIntegral) (optional v "message"::Maybe [Int])::Maybe [Word16]) $ \msg->do
 writeIORef config v;writeIORef logs [];writeIORef valuePtr ptr;writeIORef messagePtr msg
 writeIORef replies $ maybe [] id $ optional v "replies"
 writeIORef errorCodes $ map fromInteger $ maybe [] id (optional v "errors"::Maybe [Integer])
 result<-try $ do
  let name=map chr (maybe [] id $ optional v "name")
  r<-case field v "api"::String of
   "posixGet"->toJSON . map ord <$> P.getEnv name
   "posixLookup"->toJSON . fmap (map ord) <$> P.lookupEnv name
   "windowsGet"->toJSON . map ord <$> W.getEnv name
   "windowsLookup"->toJSON . fmap (map ord) <$> W.lookupEnv name
   "windowsError"->do e<-E.errCodeToIOError "getEnv" (fromInteger $ field v "code");pure $ failure $ toException e
   "map"->let EN.Errno n=E.c_maperrno_func (fromInteger $ field v "code") in pure $ toJSON (fromIntegral n::Int)
   _->error "api"
  evaluate $ L.length $ encode r
  pure r
 events<-reverse <$> readIORef logs
 pure $ object ["result" .= either failure id result,"events" .= events]
main=do
 hSetBuffering stdout LineBuffering
 input<-L.getContents
 forM_ (L.lines input) $ \line->case eitherDecode line of Left e->error e;Right v->run v >>= L.putStrLn . encode
