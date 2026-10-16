{-# LANGUAGE ForeignFunctionInterface,OverloadedStrings #-}
module Main where
import Foreign.Ptr
import Foreign.Marshal.Alloc
import Foreign.Marshal.Array
import Foreign.C.Types
import Data.Word
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import qualified Data.Text as T
import qualified OriginalEd448Spec as O
import qualified OriginalCurve448Spec as X
import Test.Hspec(hspec,describe)
import System.Environment(getArgs,withArgs)
foreign import ccall unsafe "crypton_decaf_448_point_scalarmul" f0::Ptr Word8->Ptr Word8->Ptr Word8->IO()
foreign import ccall unsafe "crypton_decaf_448_point_double_scalarmul" f1::Ptr Word8->Ptr Word8->Ptr Word8->Ptr Word8->Ptr Word8->IO()
foreign import ccall unsafe "crypton_decaf_448_point_dual_scalarmul" f2::Ptr Word8->Ptr Word8->Ptr Word8->Ptr Word8->Ptr Word8->IO()
foreign import ccall unsafe "crypton_decaf_448_precompute" f3::Ptr Word8->Ptr Word8->IO()
foreign import ccall unsafe "crypton_decaf_448_precomputed_scalarmul" f4::Ptr Word8->Ptr Word8->Ptr Word8->IO()
foreign import ccall unsafe "crypton_decaf_448_direct_scalarmul" f5::Ptr Word8->Ptr Word8->Ptr Word8->Word32->Word32->IO CInt
foreign import ccall unsafe "crypton_decaf_x448" f6::Ptr Word8->Ptr Word8->Ptr Word8->IO CInt
foreign import ccall unsafe "crypton_decaf_x448_generate_key" f7::Ptr Word8->Ptr Word8->IO()
foreign import ccall unsafe "crypton_decaf_x448_derive_public_key" f8::Ptr Word8->Ptr Word8->IO()
foreign import ccall unsafe "crypton_decaf_448_precompute_wnafs" f9::Ptr Word8->Ptr Word8->IO()
foreign import ccall unsafe "crypton_decaf_448_base_double_scalarmul_non_secret" f10::Ptr Word8->Ptr Word8->Ptr Word8->Ptr Word8->IO()
foreign import ccall unsafe "crypton_decaf_448_precomputed_destroy" f11::Ptr Word8->IO()
arg o k=case K.lookup k o of Just(String s)->T.unpack s;_->""
bytes o k=case K.lookup k o of
 Just v->case fromJSON v of Success a->a;_->error "bytes"
 Nothing->[]
withBuffers [] f=f []
withBuffers(a:rest)f=allocaBytesAligned 16000 32 $ \p->pokeArray p(take 16000(a++repeat 0x51))>>withBuffers rest(\ps->f(p:ps))
main=do
 args<-getArgs
 case args of
  "--suite":rest->withArgs rest(hspec$do describe "Ed448" O.spec;describe "Curve448" X.spec)
  _->L.getContents>>=mapM_ run.L.lines
 where
 run line=case eitherDecode line of
  Left e->error e
  Right o->withBuffers(map(bytes o)["a","b","out","out2","s1","s2","table","bytes","baseBytes","scalarBytes"]) $ \[a,b,out,out2,s1,s2,table,buf,base,secret]->do
   let word k=read(if null(arg o k)then"0"else arg o k)::Word32
   result<-case arg o "op"of
    "scalarmul"->f0 out a s1>>pure "0"
    "doubleScalarmul"->f1 out a s1 b s2>>pure "0"
    "dualScalarmul"->f2 out out2 a s1 s2>>pure "0"
    "precompute"->f3 table a>>pure "0"
    "precomputedScalarmul"->f4 out table s1>>pure "0"
    "directScalarmul"->show <$> f5 buf base s1 (word "allow") (word "shortCircuit")
    "x448"->show <$> f6 buf base secret
    "generateKey"->f7 buf secret>>pure "0"
    "derivePublicKey"->f8 buf secret>>pure "0"
    "precomputeWnafs"->f9 table a>>pure "0"
    "baseDoubleScalarmulNonSecret"->f10 out s1 a s2>>pure "0"
    "precomputedDestroy"->f11 table>>pure "0"
    _->error "operation"
   outBytes<-peekArray 256 out;out2Bytes<-peekArray 256 out2;tableBytes<-peekArray 15360 table;wire<-peekArray 56 buf
   L.putStrLn$encode$object["out" .= (outBytes::[Word8]),"out2" .= (out2Bytes::[Word8]),"table" .= (tableBytes::[Word8]),"bytes" .= (wire::[Word8]),"value" .= (result::String)]
