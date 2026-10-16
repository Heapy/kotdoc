{-# LANGUAGE OverloadedStrings, ScopedTypeVariables, CPP, MagicHash #-}
#if __GLASGOW_HASKELL__ != 912 || __GLASGOW_HASKELL_PATCHLEVEL1__ != 2 || __GLASGOW_HASKELL_PATCHLEVEL2__ != 0
#error GHC 9.12.2 required
#endif
module Main where
import Data.Aeson hiding (encode)
import qualified Data.Aeson as J
import qualified Data.Aeson.KeyMap as KM
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Char
import GHC.Exts (Int(I#),Char(C#),chr#)
import Data.Word
import Data.IORef
import Foreign
import Foreign.C.Types(CInt)
import Control.Exception
import Control.Monad
import GHC.IO.Exception(IOException(..))
import GHC.Internal.IO.Buffer
import GHC.IO.Encoding.Types
import GHC.IO.Encoding.Failure
import qualified GHC.Internal.Foreign.C.String.Encoding as E
import qualified OriginalCodePage as C
import qualified OriginalCodePageAPI as A
import OriginalCodePageTable
import Data.Maybe(fromMaybe)
import qualified OriginalWindowsEncoding as W
import qualified CodePageEffects as F
import qualified OriginalLocaleCodePage as S

unsafeChr (I# n)=C# (chr# n)
field v k=case fromJSON (maybe (error "missing field") id (KM.lookup k v)) of Success a->a;Error e->error e
optional v k def=case KM.lookup k v of Nothing->def;Just x->case fromJSON x of Success a->a;Error e->error e
failure (e::SomeException)=case fromException e of
 Just (F.WinFailure loc code)->object ["error" .= ("win32"::String),"location" .= loc,"code" .= code]
 Nothing->case fromException e of
  Just (IOError _ typ loc desc errno filename)->object ["error" .= ("io"::String),"type" .= show typ,"location" .= map ord loc,"description" .= map ord desc,"errno" .= fmap (fromIntegral::CInt->Int) errno,"filename" .= fmap (map ord) filename]
  Nothing->object ["error" .= ("fatal"::String),"message" .= (takeWhile (/='\n') $ displayException e)]
capture action=do
 r<-try $ do v<-action;evaluate $ L.length $ J.encode v;pure v
 pure $ either failure id r
mode v=case field v "mode"::String of "Error"->ErrorOnCodingFailure;"Ignore"->IgnoreCodingFailure;"Transliterate"->TransliterateCodingFailure;"Roundtrip"->RoundtripFailure;_->error "mode"
buffer :: forall a. Storable a => [a] -> Int -> Int -> Int -> Bool -> IO (Buffer a)
buffer xs size left right output=do
 b<-newBuffer ((size + 64) * sizeOf (undefined::a)) size (if output then WriteBuffer else ReadBuffer)
 withBuffer b $ \p->pokeArray p xs
 pure b {bufL=left,bufR=right}
metadata b=toJSON [bufSize b,bufL b,bufR b]
step v=do
 let encoding=field v "direction"==("encode"::String)
     cp=field v "cp"::Word32
     xs=field v "input"::[Word32]
     il=field v "left";ir=field v "right";os=field v "capacity";ol=field v "outLeft";orr=field v "outRight"
 if encoding then case C.mkCodePageEncoding (mode v) cp of
  TextEncoding {mkTextEncoder=make}->do
   i<-buffer (map (unsafeChr.fromIntegral) xs ++ replicate 64 (unsafeChr 90)) (length xs) il ir False
   o<-buffer (replicate os 0xcc::[Word8]) os ol orr True
   enc<-make
   (why,i',o')<-encode enc i o
   ys<-withBuffer o' $ \p->map fromIntegral <$> peekArray (bufR o'-bufL o') (p `advancePtr` bufL o')
   pure $ object ["why" .= show why,"input" .= metadata i',"output" .= metadata o',"values" .= (ys::[Word32])]
 else case C.mkCodePageEncoding (mode v) cp of
  TextEncoding {mkTextDecoder=make}->do
   i<-buffer (map fromIntegral xs ++ replicate 64 90::[Word8]) (length xs) il ir False
   o<-buffer (replicate os (chr 0xcccc)) os ol orr True
   dec<-make
   (why,i',o')<-encode dec i o
   ys<-withBuffer o' $ \p->map (fromIntegral.ord) <$> peekArray (bufR o'-bufL o') (p `advancePtr` bufL o')
   pure $ object ["why" .= show why,"input" .= metadata i',"output" .= metadata o',"values" .= (ys::[Word32])]
utf16 v=do
 let xs=field v "input"::[Word32];il=field v "left";ir=field v "right";os=field v "capacity";ol=field v "outLeft";orr=field v "outRight"
 if field v "direction"==("encode"::String) then do
  i<-buffer (map (unsafeChr.fromIntegral) xs) (length xs) il ir False
  o<-buffer (replicate os 0xcccc::[Word16]) os ol orr True
  (why,i',o')<-A.utf16_native_encode i o
  ys<-withBuffer o' $ \p->map fromIntegral <$> peekArray (bufR o'-bufL o') (p `advancePtr` bufL o')
  pure $ object ["why" .= show why,"input" .= metadata i',"output" .= metadata o',"values" .= (ys::[Word32])]
 else do
  i<-buffer (map fromIntegral xs::[Word16]) (length xs) il ir False
  o<-buffer (replicate os (unsafeChr 0xcccc)) os ol orr True
  (why,i',o')<-A.utf16_native_decode i o
  ys<-withBuffer o' $ \p->map (fromIntegral.ord) <$> peekArray (bufR o'-bufL o') (p `advancePtr` bufL o')
  pure $ object ["why" .= show why,"input" .= metadata i',"output" .= metadata o',"values" .= (ys::[Word32])]
locale v=do
 token<-newIORef ()
 let init=S.newLocale (readIORef token >> C.getCurrentCodePage)
 results<-forM (field v "actions"::[Object]) $ \a->capture $ case field a "op"::String of
  "console"->modifyIORef' F.config (KM.insert "console" $ toJSON (field a "value"::Word32)) >> pure Null
  "acp"->modifyIORef' F.config (KM.insert "acp" $ toJSON (field a "value"::Word32)) >> pure Null
  "direct"->toJSON <$> C.getCurrentCodePage
  "name"->pure $ toJSON $ textEncodingName $ init (mode a)
  "decode"->withArrayLen (map fromIntegral (field a "bytes"::[Int])::[Word8]) $ \n p->toJSON . map (fromIntegral.ord::Char->Word32) <$> E.peekCStringLen (init $ mode a) (castPtr p,n)
  _->error "locale action"
 pure $ toJSON results
whole v=do
 enc<-if optional v "named" False then W.mkTextEncoding' (mode v) (field v "name") else pure $ C.mkCodePageEncoding (mode v) (field v "cp")
 values<-forM (field v "batches"::[[Word32]]) $ \xs->capture $ case field v "direction"::String of
  "decode"->withArrayLen (map fromIntegral xs::[Word8]) $ \n p->toJSON . map (fromIntegral.ord::Char->Word32) <$> E.peekCStringLen enc (castPtr p,n)
  "encode"->(if optional v "terminate" True then E.withCStringLen0 else E.withCStringLen) enc (map (unsafeChr.fromIntegral) xs) $ \(p,n)->toJSON . map (fromIntegral::Word8->Int) <$> peekArray n (castPtr p)
  "representable"->toJSON <$> mapM (E.charIsRepresentable enc . unsafeChr . fromIntegral) xs
  _->error "direction"
 pure $ object ["name" .= textEncodingName enc,"values" .= values]
run v=do
 F.reset v
 result<-capture $ case field v "api"::String of
  "table"->do
   let Just (SingleByteCP dec (CompactArray mx indices vals))=lookup (field v "cp") codePageMap
   pure $ object ["decode" .= [fromIntegral (ord (C.lookupConv dec b))::Word32 | b<-[0..255]],"encode" .= [maybe (-1) fromIntegral $ C.lookupCompact mx indices vals (chr n) | n<-[0..ord mx]],"max" .= ord mx]
  "utf16"->utf16 v
  "locale"->locale v
  "view"->do
   b<-buffer ([]::[Word16]) (field v "size") (field v "left") (field v "right") False
   let b'=b {bufOffset=123}
       out=if field v "direction"==("byte"::String) then let x=A.byteView b' in object ["buffer" .= metadata x,"offset" .= bufOffset x] else let x=A.cwcharView (b' {bufRaw=castForeignPtr $ bufRaw b'}) in object ["buffer" .= metadata x,"offset" .= bufOffset x]
   pure out
  "whole"->whole v
  "step"->step v
  "current"->toJSON <$> C.getCurrentCodePage
  "cpinfo"->allocaBytes 20 $ \p->do
   let info=A.CPINFO (field v "max") (field v "default") (field v "lead")
   poke p info
   info'<-peek p
   pure $ object ["size" .= sizeOf info',"alignment" .= alignment info',"max" .= A.maxCharSize info',"default" .= A.defaultChar info',"lead" .= A.leadByte info']
  _->error "api"
 trace<-reverse <$> readIORef F.logs
 pure $ object ["value" .= result,"trace" .= trace]
main=L.getContents >>= mapM_ (\line->case eitherDecode line of Left e->error e;Right v->run v >>= L.putStrLn . J.encode) . L.lines
