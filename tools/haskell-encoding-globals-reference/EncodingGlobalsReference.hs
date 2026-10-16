{-# LANGUAGE OverloadedStrings,ScopedTypeVariables,CPP,ForeignFunctionInterface #-}
#if __GLASGOW_HASKELL__ != 912 || __GLASGOW_HASKELL_PATCHLEVEL1__ != 2 || __GLASGOW_HASKELL_PATCHLEVEL2__ != 0
#error GHC 9.12.2 required
#endif
module Main where
import Data.Aeson
import qualified Data.Aeson.KeyMap as KM
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.Map.Lazy as M
import Data.Char
import Data.IORef
import Foreign
import Foreign.C.Types (CInt)
import qualified Foreign.C.Error as Errno
import Control.Exception
import Control.Monad
import GHC.IO.Exception (IOException(..))
import GHC.IO.Encoding (TextEncoding,mkTextEncoding)
import qualified GHC.IO.Encoding as Actual
import GHC.IO.Encoding.Types (textEncodingName)
import qualified GHC.Internal.Foreign.C.String.Encoding as Enc
import qualified Foreign.C.String as CString
import qualified System.Environment as Env
import System.IO.Unsafe (unsafePerformIO)
import System.IO (hSetBuffering,stdout,BufferMode(..))
import qualified OriginalEncodingGlobals as G
import qualified OriginalInitialEncoding as I
import GHC.IO.Encoding.Failure (CodingFailureMode(..))

field v k=case fromJSON (maybe (error "missing field") id (KM.lookup k v)) of Success a->a;Error e->error e
optional v k=case KM.lookup k v of Nothing->Nothing;Just Null->Nothing;Just x->case fromJSON x of Success a->Just a;Error e->error e
failure (e::SomeException)=case fromException e of
 Just (IOError _ typ loc desc errno filename)->object ["error" .= ("io"::String),"type" .= show typ,"location" .= map ord loc,"description" .= map ord desc,"errno" .= fmap (fromIntegral :: CInt -> Int) errno,"filename" .= fmap (map ord) filename]
 Nothing->case fromException e of
  Just (AssertionFailed msg)->object ["error" .= ("fatal"::String),"message" .= msg]
  _->object ["error" .= ("unexpected"::String),"message" .= displayException e]
capture action=do
 r<-try $ do v<-action;evaluate $ L.length $ encode v;pure v
 pure $ either failure id r
raise s=if take 3 s=="io:" then ioError (userError s) else throwIO (AssertionFailed s)
make logs label effect=unsafePerformIO $ do
 modifyIORef' logs (label:)
 effect
{-# NOINLINE make #-}
get g slot=case slot of "locale"->G.getLocale g;"filesystem"->G.getFilesystem g;"foreign"->G.getForeign g;_->error "slot"
set g slot=case slot of "locale"->G.setLocale g;"filesystem"->G.setFilesystem g;"foreign"->G.setForeign g;_->error "slot"
mode "locale"=ErrorOnCodingFailure
mode "filesystem"=RoundtripFailure
mode "foreign"=IgnoreCodingFailure
initial initials slot=case slot of "locale"->initials!!0;"filesystem"->initials!!1;"foreign"->initials!!2;_->error "slot"
operate enc a=case field a "op"::String of
 "name"->pure $ toJSON $ textEncodingName enc
 "decode"->withArrayLen (map fromIntegral (field a "bytes"::[Int])::[Word8]) $ \n p->toJSON . map ord <$> Enc.peekCStringLen enc (castPtr p,n)
 "encode"->(if field a "terminate" then Enc.withCStringLen0 else Enc.withCStringLen) enc (map chr $ field a "points") $ \(p,n)->toJSON . map (fromIntegral::Word8->Int) <$> peekArray n (castPtr p)
 "representable"->toJSON <$> Enc.charIsRepresentable enc (chr $ field a "point")
 _->error "operate"
controlled v=do
 logs<-newIORef []
 charset<-newIORef (field v "charset"::String)
 saved<-newIORef (M.empty :: M.Map String TextEncoding)
 let charsetName=make logs "charset" $ do
       case optional v "charsetFailure" of Just s->raise s;Nothing->pure ()
       readIORef charset
     mkInit slot=make logs ("initial:"++slot) $ I.mkTextEncoding' (mode slot) charsetName
     initials=map mkInit ["locale","filesystem","foreign"]
     g=G.newGlobals (initials!!0) (initials!!1) (initials!!2)
     select a=case optional a "saved" of
       Just key->(M.! key) <$> readIORef saved
       Nothing->case field a "source"::String of
        "current"->get g (field a "slot")
        "initial"->pure $ initial initials (field a "slot")
        "argv"->if field v "windows" then G.windowsArgvEncoding else G.posixArgvEncoding g
        _->error "source"
 results<-forM (field v "actions"::[Object]) $ \a->capture $ case field a "op"::String of
  "read"->select a >>= \enc->modifyIORef' saved (M.insert (field a "key") enc) >> pure Null
  "install"->do
   let enc=make logs ("installed:"++(field a "tag")) $ case optional a "failure" of
         Just s->raise s
         Nothing->mkTextEncoding (field a "encoding")
   set g (field a "slot") enc
   pure Null
  "restoreInitial"->set g (field a "slot") (initial initials $ field a "slot") >> pure Null
  "charset"->writeIORef charset (field a "value") >> pure Null
  _->select a >>= \enc->operate enc a
 events<-reverse <$> readIORef logs
 pure $ object ["results" .= results,"events" .= events]

foreign import ccall unsafe "kotdoc_environ_address" environmentAddress :: IO (Ptr (Ptr (Ptr Word8)))
withRecords Nothing action=action nullPtr
withRecords (Just records) action=withMany (withArray0 0 . map fromIntegral) records $ \ptrs->withArray0 nullPtr ptrs action
setActual slot=case slot of "locale"->Actual.setLocaleEncoding;"filesystem"->Actual.setFileSystemEncoding;"foreign"->Actual.setForeignEncoding;_->error "slot"
getActual slot=case slot of "locale"->Actual.getLocaleEncoding;"filesystem"->Actual.getFileSystemEncoding;"foreign"->Actual.getForeignEncoding;_->error "slot"
native v=do
 oldL<-Actual.getLocaleEncoding;oldFS<-Actual.getFileSystemEncoding;oldF<-Actual.getForeignEncoding
 address<-environmentAddress
 bracket (peek address) (\p->poke address p >> Actual.setLocaleEncoding oldL >> Actual.setFileSystemEncoding oldFS >> Actual.setForeignEncoding oldF) $ \_->
  withRecords (field v "records"::Maybe [[Int]]) $ \block->do
   poke address block
   results<-forM (field v "actions"::[Object]) $ \a->capture $ case field a "op"::String of
    "set"->do
     enc<-mkTextEncoding (field a "encoding")
     setActual (field a "slot") enc
     pure Null
    "setDeferred"->do
     let enc=unsafePerformIO $ case optional a "failure" of
           Just s->raise s
           Nothing->do
            when (maybe False id $ optional a "swapSecondWithThird") $ do
             current<-peek address
             peekElemOff current 2 >>= pokeElemOff current 1
            case optional a "during" of
             Nothing->pure ()
             Just target->mkTextEncoding target >>= setActual (field a "slot")
            mkTextEncoding (field a "encoding")
     setActual (field a "slot") enc
     pure Null
    "name"->getActual (field a "slot") >>= pure . toJSON . textEncodingName
    "environment"->do rows<-Env.getEnvironment;pure $ toJSON [[map ord k,map ord x]|(k,x)<-rows]
    "lookup"->toJSON . fmap (map ord) <$> Env.lookupEnv (map chr $ field a "name")
    "errno"->pure $ failure $ toException $ Errno.errnoToIOError "global-errno" (Errno.Errno $ fromIntegral (field a "code"::Int)) Nothing Nothing
    "foreignEncode"->CString.withCStringLen (map chr $ field a "points") $ \(p,n)->toJSON . map (fromIntegral::Word8->Int) <$> peekArray n (castPtr p)
    "foreignDecode"->withArrayLen (map fromIntegral (field a "bytes"::[Int])::[Word8]) $ \n p->toJSON . map ord <$> CString.peekCStringLen (castPtr p,n)
    _->error "native op"
   pure $ object ["results" .= results]
run v=if field v "api"==("native"::String) then native v else controlled v
main=do
 hSetBuffering stdout LineBuffering
 L.getContents >>= mapM_ (\line->case eitherDecode line of Left e->error e;Right v->run v >>= L.putStrLn . encode) . L.lines
