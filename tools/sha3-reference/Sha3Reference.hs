{-# LANGUAGE DataKinds,ScopedTypeVariables,TypeApplications,OverloadedStrings #-}
module Main where
import Crypto.Hash
import Crypto.Hash.SHAKE
import Crypto.Hash.Types(Context,Digest,HashAlgorithm(..))
import qualified Data.ByteArray as A
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import GHC.TypeLits
import Data.Proxy
import Data.Word
import Foreign.Marshal.Alloc
import Foreign.Marshal.Utils(copyBytes)
import Foreign.Marshal.Array
import Foreign.Ptr
import qualified OriginalSha3HashSpec as O
import Test.Hspec(hspec)
import System.Environment(getArgs,withArgs)
number o k=case K.lookup k o of Just(Number n)->round n;_->0
chunks o=case K.lookup "chunks" o of
 Just v->case fromJSON v of Success xs->map B.pack(xs::[[Word8]]);_->error "chunks"
 Nothing->[]
finish :: forall a. (HashSHAKE a,Show a)=>a->[B.ByteString]->Bool->IO Value
finish alg input cshake=do
 result<-if not cshake then pure(B.unpack(A.convert(hashFinalize ctx)::B.ByteString))else allocaBytes(hashDigestSize alg)$ \out->A.withByteArray ctx(\p->allocaBytesAligned(hashInternalContextSize alg)8 $ \copy->copyBytes copy p(hashInternalContextSize alg)>>cshakeInternalFinalize(castPtr copy::Ptr(Context a))(castPtr out::Ptr(Digest a)))>>peekArray(hashDigestSize alg)out
 pure$object["digest" .= (result::[Word8]),"outputBits" .= cshakeOutputLength alg,"blockSize" .= hashBlockSize alg,"digestSize" .= hashDigestSize alg,"contextSize" .= hashInternalContextSize alg,"show" .= showName]
 where ctx=foldl hashUpdate(hashInitWith alg)input
       showName=show alg
observe o=case someNatVal(number o "outputBits")of
 Nothing->error "negative Nat"
 Just(SomeNat(_::Proxy n))->if number o "securityBits"==128 then finish(SHAKE128::SHAKE128 n)(chunks o)(number o "pad"==4)else finish(SHAKE256::SHAKE256 n)(chunks o)(number o "pad"==4)
main=do
 args<-getArgs
 case args of
  "--suite":rest->withArgs rest(hspec O.spec)
  _->L.getContents>>=mapM_ run.L.lines
 where run line=case eitherDecode line of Left e->error e;Right o->observe o>>=L.putStrLn.encode
