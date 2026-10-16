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
foreign import ccall unsafe "crypton_decaf_448_point_encode" enc::Ptr Word8->Ptr Word8->IO()
foreign import ccall unsafe "crypton_decaf_448_point_decode" dec::Ptr Word8->Ptr Word8->Word32->IO CInt
foreign import ccall unsafe "crypton_decaf_448_point_add" add::Ptr Word8->Ptr Word8->Ptr Word8->IO()
foreign import ccall unsafe "crypton_decaf_448_point_sub" sub::Ptr Word8->Ptr Word8->Ptr Word8->IO()
foreign import ccall unsafe "crypton_decaf_448_point_double" dbl::Ptr Word8->Ptr Word8->IO()
foreign import ccall unsafe "crypton_decaf_448_point_negate" neg::Ptr Word8->Ptr Word8->IO()
foreign import ccall unsafe "crypton_decaf_448_point_eq" eq::Ptr Word8->Ptr Word8->IO Word32
foreign import ccall unsafe "crypton_decaf_448_point_valid" valid::Ptr Word8->IO Word32
foreign import ccall unsafe "crypton_decaf_448_point_debugging_torque" torque::Ptr Word8->Ptr Word8->IO()
foreign import ccall unsafe "crypton_decaf_448_point_debugging_pscale" pscale::Ptr Word8->Ptr Word8->Ptr Word8->IO()
foreign import ccall unsafe "crypton_decaf_448_point_cond_sel" sel::Ptr Word8->Ptr Word8->Ptr Word8->Word32->IO()
foreign import ccall unsafe "crypton_decaf_448_point_mul_by_ratio_and_encode_like_eddsa" encEd::Ptr Word8->Ptr Word8->IO()
foreign import ccall unsafe "crypton_decaf_448_point_decode_like_eddsa_and_mul_by_ratio" decEd::Ptr Word8->Ptr Word8->IO CInt
foreign import ccall unsafe "crypton_decaf_448_point_mul_by_ratio_and_encode_like_x448" encX::Ptr Word8->Ptr Word8->IO()
foreign import ccall unsafe "crypton_decaf_ed448_convert_public_key_to_x448" edToX::Ptr Word8->Ptr Word8->IO()
foreign import ccall unsafe "crypton_decaf_448_point_destroy" destroy::Ptr Word8->IO()
foreign import ccall unsafe "crypton_decaf_448_deisogenize" deiso::Ptr Word8->Ptr Word8->Ptr Word8->Ptr Word8->Word64->Word64->Word64->IO()

arg o k=case K.lookup k o of Just(String s)->T.unpack s;_->""
bytes o k=case K.lookup k o of
 Just v->case fromJSON v of Success a->a;_->error "bytes"
 Nothing->[]
withShape a f=allocaBytesAligned 256 32 $ \p->pokeArray p(take 256(a++repeat 0))>>f p
main=do
 args<-getArgs
 case args of
  "--suite":rest->withArgs rest(hspec$do describe "Ed448" O.spec;describe "Curve448" X.spec)
  _->L.getContents>>=mapM_ run.L.lines
 where
 run line=case eitherDecode line of
  Left e->error e
  Right o->withShape(bytes o "a") $ \a->withShape(bytes o "b") $ \b->withShape(bytes o "out") $ \out->withShape(bytes o "bytes") $ \buf->do
   let word k=read(if null(arg o k)then"0"else arg o k)::Word64
   result<-case arg o "op"of
    "deisogenize"->deiso out(out`plusPtr`64)(out`plusPtr`128)a(word "mask")(word "mask2")(word "mask3")>>pure "0"
    "encode"->enc buf a>>pure "0"
    "decode"->show <$> dec out buf(fromIntegral(word "mask"))
    "add"->add out a b>>pure "0"
    "sub"->sub out a b>>pure "0"
    "double"->dbl out a>>pure "0"
    "negate"->neg out a>>pure "0"
    "equal"->show <$> eq a b
    "valid"->show <$> valid a
    "torque"->torque out a>>pure "0"
    "pscale"->pscale out a buf>>pure "0"
    "select"->sel out a b(fromIntegral(word "mask"))>>pure "0"
    "encodeEd"->encEd buf a>>pure "0"
    "decodeEd"->show <$> decEd out buf
    "encodeX"->encX buf a>>pure "0"
    "edPublicToX"->edToX buf(buf`plusPtr`64)>>pure "0"
    "destroy"->destroy out>>pure "0"
    _->error "operation"
   resultBytes<-peekArray 256 out
   wire<-peekArray 128 buf
   L.putStrLn$encode$object["out" .= (resultBytes::[Word8]),"bytes" .= (wire::[Word8]),"value" .= (result::String)]
