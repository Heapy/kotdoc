{-# LANGUAGE ForeignFunctionInterface,OverloadedStrings #-}
module Main where
import Foreign.Ptr
import Foreign.Marshal.Alloc
import Foreign.Marshal.Array
import Foreign.C.Types
import Data.Word
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import qualified Data.Text as T
import qualified OriginalEd448Spec as O
import qualified OriginalCurve448Spec as X
import Test.Hspec(hspec,describe)
import System.Environment(getArgs,withArgs)
foreign import ccall unsafe "crypton_decaf_448_scalar_decode_long" decLong::Ptr Word8->Ptr Word8->CSize->IO()
foreign import ccall unsafe "crypton_decaf_448_scalar_decode" dec::Ptr Word8->Ptr Word8->IO CInt
foreign import ccall unsafe "crypton_decaf_448_scalar_add" add::Ptr Word8->Ptr Word8->Ptr Word8->IO()
foreign import ccall unsafe "crypton_decaf_448_scalar_sub" sub::Ptr Word8->Ptr Word8->Ptr Word8->IO()
foreign import ccall unsafe "crypton_decaf_448_scalar_mul" mul::Ptr Word8->Ptr Word8->Ptr Word8->IO()
foreign import ccall unsafe "crypton_decaf_448_scalar_halve" halve::Ptr Word8->Ptr Word8->IO()
foreign import ccall unsafe "crypton_decaf_448_scalar_invert" inv::Ptr Word8->Ptr Word8->IO CInt
foreign import ccall unsafe "crypton_decaf_448_scalar_cond_sel" sel::Ptr Word8->Ptr Word8->Ptr Word8->Word32->IO()
foreign import ccall unsafe "crypton_decaf_448_scalar_eq" eq::Ptr Word8->Ptr Word8->IO Word32
foreign import ccall unsafe "crypton_decaf_448_scalar_set_unsigned" set::Ptr Word8->Word64->IO()
foreign import ccall unsafe "crypton_decaf_448_scalar_encode" enc::Ptr Word8->Ptr Word8->IO()
foreign import ccall unsafe "crypton_decaf_448_scalar_destroy" destroy::Ptr Word8->IO()
arg o k=case K.lookup k o of Just(String s)->T.unpack s;_->""
bytes o k=case K.lookup k o of
 Just v->case fromJSON v of Success a->a;_->error "bytes"
 Nothing->[]
withScalar a f=allocaBytes 56$ \p->pokeArray p(take 56(a++repeat 0))>>f p
main=do
 args<-getArgs
 case args of
  "--suite":rest->withArgs rest(hspec$do describe "Ed448" O.spec;describe "Curve448" X.spec)
  _->L.getContents>>=mapM_ run.L.lines
 where
 run line=case eitherDecode line of
  Left e->error e
  Right o->withScalar(bytes o "a")$ \a->withScalar(bytes o "b")$ \b->withScalar(bytes o "out")$ \out->do
   let input=B.pack(bytes o "bytes");w=read(if null(arg o "word")then"0"else arg o "word")::Word64
   result<-case arg o "op"of
    "decodeLong"->B.useAsCStringLen input$ \(p,n)->decLong out(castPtr p)(fromIntegral n)>>pure "0"
    "decode"->withScalar(B.unpack input)$ \p->show <$> dec out p
    "add"->add out a b>>pure "0"
    "sub"->sub out a b>>pure "0"
    "multiply"->mul out a b>>pure "0"
    "halve"->halve out a>>pure "0"
    "invert"->show <$> inv out a
    "condSelect"->sel out a b (fromIntegral w)>>pure "0"
    "equal"->show <$> eq a b
    "setUnsigned"->set out w>>pure "0"
    "destroy"->destroy out>>pure "0"
    "encode"->enc out a>>pure "0"
    _->error "operation"
   resultBytes<-peekArray 56 out
   L.putStrLn$encode$object["out" .= (resultBytes::[Word8]),"value" .= (result::String)]
