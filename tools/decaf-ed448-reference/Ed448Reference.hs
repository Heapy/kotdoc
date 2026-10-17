{-# LANGUAGE ForeignFunctionInterface,OverloadedStrings #-}
module Main where
import Foreign.Ptr
import Foreign.Marshal.Alloc
import Foreign.Marshal.Array
import Foreign.C.Types
import Data.Word
import Data.String(fromString)
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import qualified Data.Text as T
import qualified OriginalEd448Spec as O
import Test.Hspec(hspec)
import System.Environment(getArgs,withArgs)
foreign import ccall unsafe "crypton_decaf_ed448_prehash_init" prehashInit::Ptr Word8->IO()
foreign import ccall unsafe "crypton_sha3_update" prehashUpdate::Ptr Word8->Ptr Word8->Word32->IO()
foreign import ccall unsafe "crypton_decaf_bzero" destroy::Ptr Word8->CSize->IO()
foreign import ccall unsafe "crypton_decaf_ed448_convert_private_key_to_x448" convert::Ptr Word8->Ptr Word8->IO()
foreign import ccall unsafe "crypton_decaf_ed448_derive_public_key" derive::Ptr Word8->Ptr Word8->IO()
foreign import ccall unsafe "crypton_decaf_ed448_sign" sign::Ptr Word8->Ptr Word8->Ptr Word8->Ptr Word8->CSize->Word8->Ptr Word8->Word8->IO()
foreign import ccall unsafe "crypton_decaf_ed448_sign_prehash" signPrehash::Ptr Word8->Ptr Word8->Ptr Word8->Ptr Word8->Ptr Word8->Word8->IO()
foreign import ccall unsafe "crypton_decaf_ed448_derive_keypair" deriveKeypair::Ptr Word8->Ptr Word8->IO()
foreign import ccall unsafe "crypton_decaf_ed448_keypair_extract_public_key" extractPublic::Ptr Word8->Ptr Word8->IO()
foreign import ccall unsafe "crypton_decaf_ed448_keypair_extract_private_key" extractPrivate::Ptr Word8->Ptr Word8->IO()
foreign import ccall unsafe "crypton_decaf_ed448_keypair_destroy" destroyKeypair::Ptr Word8->IO()
foreign import ccall unsafe "crypton_decaf_ed448_keypair_sign" keypairSign::Ptr Word8->Ptr Word8->Ptr Word8->CSize->Word8->Ptr Word8->Word8->IO()
foreign import ccall unsafe "crypton_decaf_ed448_keypair_sign_prehash" keypairSignPrehash::Ptr Word8->Ptr Word8->Ptr Word8->Ptr Word8->Word8->IO()
foreign import ccall unsafe "crypton_decaf_ed448_verify" verify::Ptr Word8->Ptr Word8->Ptr Word8->CSize->Word8->Ptr Word8->Word8->IO CInt
foreign import ccall unsafe "crypton_decaf_ed448_verify_prehash" verifyPrehash::Ptr Word8->Ptr Word8->Ptr Word8->Ptr Word8->Word8->IO CInt
bytes o k=case K.lookup k o of
 Nothing->[]
 Just v->case fromJSON v of Success a->a;_->error "bytes"
str o k=case K.lookup k o of Just(String v)->T.unpack v;_->""
number o k=case K.lookup k o of
 Nothing->0
 Just v->case fromJSON v of Success a->a;_->0
flag o k=K.lookup k o==Just(Bool True)
withFrames [] f=f []
withFrames((name,values):rest)f=allocaBytesAligned(length values)32 $ \p->pokeArray p values>>withFrames rest(\ps->f((name,p):ps))
main=do
 args<-getArgs
 case args of
  "--suite":rest->withArgs rest(hspec O.spec)
  _->L.getContents>>=mapM_ run.L.lines
 where
 run line=case eitherDecode line of
  Left e->error e
  Right o->do
   let sec=bytes o "secret";pub=bytes o "public";msg=bytes o "message";context=bytes o "context";kp=case K.lookup "keypair" o of Nothing->sec++pub;_->bytes o "keypair"
       inputs=[("out",case K.lookup "out" o of Nothing->replicate 114 165;_->bytes o "out"),("secret",sec),("public",pub),("signature",bytes o "signature"),("keypair",kp),("message",msg),("context",context),("ctx",replicate 344 81)]
       frame(name,v)=(name,replicate 32 17++v++replicate((if name=="ctx"then 344 else max 114(length v))-length v)165++replicate 32 34)
   withFrames(map frame inputs)$ \ps->do
    let original k=maybe(error k)id(lookup k ps);raw k=original(if k=="out"&&not(null(str o "alias"))then str o "alias"else k);ptr k=raw k`plusPtr`32
        out=ptr "out";secret=ptr "secret";public=ptr "public";signature=ptr "signature";keypair=ptr "keypair";ctx=ptr "ctx";message=ptr "message";ct=ptr "context";n=fromIntegral(length msg);cl=fromIntegral(length context);ph=number o "prehashed"
    if flag o "prehash"then do
     prehashInit ctx
     case K.lookup "prehashChunks" o of
      Nothing->prehashUpdate ctx message(fromIntegral(length msg))
      Just value->case (fromJSON value::Result [[Word8]]) of
       Success chunks->mapM_(\chunk->allocaBytes(max 1(length chunk))$ \b->pokeArray b(chunk::[Word8])>>prehashUpdate ctx b(fromIntegral(length chunk)))chunks
       _->error "prehash chunks"
    else pure()
    ret<-case str o "op"of
     "prehashInit"->prehashInit ctx>>pure 0
     "prehashUpdate"->prehashUpdate ctx message(fromIntegral(length msg))>>pure 0
     "prehashDestroy"->destroy ctx 344>>pure 0
     "convertPrivateKeyToX448"->convert out secret>>pure 0
     "derivePublicKey"->derive out secret>>pure 0
     "sign"->sign out secret public message n ph ct cl>>pure 0
     "signPrehash"->signPrehash out secret public ctx ct cl>>pure 0
     "deriveKeypair"->deriveKeypair keypair secret>>pure 0
     "keypairExtractPublicKey"->extractPublic out keypair>>pure 0
     "keypairExtractPrivateKey"->extractPrivate out keypair>>pure 0
     "keypairDestroy"->destroyKeypair keypair>>pure 0
     "keypairSign"->keypairSign out keypair message n ph ct cl>>pure 0
     "keypairSignPrehash"->keypairSignPrehash out keypair ctx ct cl>>pure 0
     "verify"->verify signature public message n ph ct cl
     "verifyPrehash"->verifyPrehash signature public ctx ct cl
     _->error "operation"
    frames<-mapM(\(k,v)->do b<-peekArray(length(snd(frame(if k=="out"&&not(null(str o "alias"))then str o "alias"else k,maybe(error "input")id(lookup(if k=="out"&&not(null(str o "alias"))then str o "alias"else k)inputs)))))(raw k);pure(T.pack k,toJSON(b::[Word8])))inputs
    L.putStrLn$encode$object["ret" .= (fromIntegral ret::Int),"frames" .= Object(K.fromList[(fromString(T.unpack k),v)|(k,v)<-frames])]
