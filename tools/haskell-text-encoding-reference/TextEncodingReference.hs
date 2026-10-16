{-# LANGUAGE OverloadedStrings,ScopedTypeVariables,CPP #-}
#if __GLASGOW_HASKELL__ != 912 || __GLASGOW_HASKELL_PATCHLEVEL1__ != 2 || __GLASGOW_HASKELL_PATCHLEVEL2__ != 0
#error GHC 9.12.2 required
#endif
module Main where
import Data.Aeson
import qualified Data.Aeson.KeyMap as KM
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Char
import Foreign
import Foreign.C.Types (CInt)
import Control.Exception
import Control.Monad
import GHC.IO.Exception (IOException(..))
import GHC.IO.Encoding (char8,mkTextEncoding)
import GHC.IO.Encoding.Types (textEncodingName)
import qualified GHC.Internal.Foreign.C.String.Encoding as E
import System.IO (hSetBuffering,stdout,BufferMode(..))
field v k=case fromJSON (maybe (error "missing field") id (KM.lookup k v)) of Success a->a;Error e->error e
failure (IOError _ typ loc desc errno filename)=object ["error" .= ("io"::String),"type" .= show typ,"location" .= map ord loc,"description" .= map ord desc,"errno" .= fmap (fromIntegral :: CInt -> Int) errno,"filename" .= fmap (map ord) filename]
capture action=do
 r<-try $ do v<-action;evaluate $ L.length $ encode v;pure v
 pure $ either failure id r
run v=capture $ do
 enc<-if (field v "char8"::Bool) then pure char8 else mkTextEncoding (field v "encoding")
 let api=field v "api"::String
 values<-forM (field v "batches"::[[Int]]) $ \xs->capture $ do
  result<-case api of
   "decode"->withArrayLen (map fromIntegral xs::[Word8]) $ \n p->toJSON . map ord <$> E.peekCStringLen enc (castPtr p,n)
   "encode"->(if field v "terminate" then E.withCStringLen0 else E.withCStringLen) enc (map chr xs) $ \(p,n)->toJSON . map (fromIntegral::Word8->Int) <$> peekArray n (castPtr p)
   "representable"->toJSON <$> mapM (E.charIsRepresentable enc . chr) xs
   _->error "api"
  pure $ object ["value" .= result]
 pure $ object ["name" .= textEncodingName enc,"values" .= values]
main=do
 hSetBuffering stdout LineBuffering
 L.getContents >>= mapM_ (\line->case eitherDecode line of Left e->error e;Right v->run v >>= L.putStrLn . encode) . L.lines
