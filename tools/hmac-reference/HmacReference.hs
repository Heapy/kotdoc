{-# LANGUAGE ScopedTypeVariables, OverloadedStrings, ExistentialQuantification, DataKinds #-}
module Main where
import Crypto.Hash
import qualified Crypto.MAC.HMAC as H
import qualified Data.ByteArray as A
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import qualified Data.Text as T
import Data.Word
import qualified OriginalHmacSpec as O
import Test.Hspec(hspec)
import Test.QuickCheck(arbitrary)
import Test.QuickCheck.Gen(unGen)
import Test.QuickCheck.Random(mkQCGen)
import System.Environment(getArgs,withArgs)
get o k=case K.lookup k o of Just v->v;_->Null
bytes v=case fromJSON v of Success xs->B.pack(xs::[Word8]);_->B.empty
chunks o=case get o "chunks"of Array xs->map bytes(foldr(:)[]xs);_->[]
data Alg=forall a.HashAlgorithm a=>Alg a
algorithms=[("MD5",Alg MD5),("SHA1",Alg SHA1),("SHA224",Alg SHA224),("SHA256",Alg SHA256),("SHA384",Alg SHA384),("SHA512",Alg SHA512),("SHA512t_224",Alg SHA512t_224),("SHA512t_256",Alg SHA512t_256),("Keccak_224",Alg Keccak_224),("Keccak_256",Alg Keccak_256),("Keccak_384",Alg Keccak_384),("Keccak_512",Alg Keccak_512),("SHA3_224",Alg SHA3_224),("SHA3_256",Alg SHA3_256),("SHA3_384",Alg SHA3_384),("SHA3_512",Alg SHA3_512),("SHAKE128_0",Alg(SHAKE128::SHAKE128 0)),("SHAKE128_1",Alg(SHAKE128::SHAKE128 1)),("SHAKE128_7",Alg(SHAKE128::SHAKE128 7)),("SHAKE128_8",Alg(SHAKE128::SHAKE128 8)),("SHAKE128_224",Alg(SHAKE128::SHAKE128 224)),("SHAKE128_1344",Alg(SHAKE128::SHAKE128 1344)),("SHAKE128_2048",Alg(SHAKE128::SHAKE128 2048)),("SHAKE256_0",Alg(SHAKE256::SHAKE256 0)),("SHAKE256_1",Alg(SHAKE256::SHAKE256 1)),("SHAKE256_7",Alg(SHAKE256::SHAKE256 7)),("SHAKE256_8",Alg(SHAKE256::SHAKE256 8)),("SHAKE256_224",Alg(SHAKE256::SHAKE256 224)),("SHAKE256_1344",Alg(SHAKE256::SHAKE256 1344)),("SHAKE256_2048",Alg(SHAKE256::SHAKE256 2048))]
observe o=if get o "equalOnly"==Bool True then object["equal" .= A.constEq(bytes(get o "key"))(bytes(get o "other"))]else case get o "algorithm"of String name->case lookup name algorithms of Just(Alg alg)->finish alg o;_->error"algorithm";_->error"request"
finish :: forall a.HashAlgorithm a=>a->Object->Value
finish alg o=object$["mac" .= mac one,"incremental" .= map(mac.H.finalize) states,"lazy" .= mac lazyMac,"digest" .= B.unpack(A.convert(H.hmacGetDigest one)::B.ByteString),"equalSelf" .= (one==one),"equalOther" .= (one==other)] ++ if get o "publicDigest"==Bool True then ["contextLengths" .= contextLengths,"digestShow" .= show(H.hmacGetDigest one),"digestCompare" .= show(compare(H.hmacGetDigest one)(H.hmacGetDigest other)),"reads" .= parsed,"fromLengths" .= lengths]else []
 where
  key=bytes(get o "key");xs=chunks o
  one=H.hmac key(B.concat xs)::H.HMAC a
  lazyMac=H.hmacLazy key(L.fromChunks xs)::H.HMAC a
  states=scanl H.update(H.initialize key::H.Context a)xs
  other=H.hmac key(bytes(get o "other"))::H.HMAC a
  contextLengths=case head states of H.Context outer inner->[A.length outer,A.length inner]
  input=case fromJSON(get o "read")of Success v->v;_->""
  parsed=[object["bytes" .= B.unpack(A.convert d::B.ByteString),"rest" .= rest]|(d,rest)<-(reads input::[(Digest a,String)])]
  lengths=[case(digestFromByteString(B.replicate n 0)::Maybe(Digest a))of Nothing->False;Just _->True|n<-[0,max 0(hashDigestSize alg-1),hashDigestSize alg,hashDigestSize alg+1]]
  mac :: H.HMAC a->[Word8]
  mac x=B.unpack(A.convert x::B.ByteString)
samples=concat[rows (T.unpack name) alg |(name,Alg alg)<-algorithms,name `elem` ["MD5","SHA1","SHA256","SHA3_224","SHA3_256","SHA3_384","SHA3_512"]]
 where
  rows :: forall a.HashAlgorithm a=>String->a->[Value]
  rows name alg=concat[[row "one" key [message]|let O.MacIncremental key message _=(unGen arbitrary(mkQCGen(613+i))(i`mod`101)::O.MacIncremental a)]++[row "multi" key messages|let O.MacIncrementalList key messages _=(unGen arbitrary(mkQCGen(1613+i))(i`mod`101)::O.MacIncrementalList a)]|i<-[0..99]]
   where row family key messages=let request=object["algorithm" .= name,"key" .= B.unpack key,"chunks" .= map B.unpack messages,"sampleFamily" .= (family::String)];Object o=request in object["request" .= request,"expected" .= finish alg o]
main=do
 args<-getArgs
 case args of
  ["--literals"]->mapM_(L.putStrLn.encode) literalRows
  ["--samples"]->mapM_(L.putStrLn.encode)samples
  "--suite":rest->withArgs rest(hspec O.spec)
  _->L.getContents>>=mapM_ run.L.lines
 where run line=case eitherDecode line of Left e->error e;Right o->L.putStrLn(encode(observe o))

literalRows=concat[rows "MD5" O.md5MACVectors,rows "SHA1" O.sha1MACVectors,rows "SHA256" O.sha256MACVectors,rows "Keccak_224" O.keccak_224_MAC_Vectors,rows "Keccak_256" O.keccak_256_MAC_Vectors,rows "Keccak_384" O.keccak_384_MAC_Vectors,rows "Keccak_512" O.keccak_512_MAC_Vectors,rows "SHA3_224" O.sha3_224_MAC_Vectors,rows "SHA3_256" O.sha3_256_MAC_Vectors,rows "SHA3_384" O.sha3_384_MAC_Vectors,rows "SHA3_512" O.sha3_512_MAC_Vectors]
 where
  rows :: String->[O.MACVector a]->[Value]
  rows name vectors=[object["algorithm" .= name,"key" .= B.unpack(O.macKey v),"message" .= B.unpack(O.macSecret v),"expected" .= B.unpack(A.convert(O.macResult v)::B.ByteString)]|v<-vectors]
