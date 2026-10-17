{-# LANGUAGE ScopedTypeVariables, OverloadedStrings, ExistentialQuantification #-}
module Main where
import Crypto.Hash
import qualified Data.ByteArray as A
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import qualified Utils as U
import Test.QuickCheck(arbitrary,choose)
import Test.QuickCheck.Gen(unGen)
import Test.QuickCheck.Random(mkQCGen)
import Data.Word
import Data.Proxy
import Control.DeepSeq(force)
import Control.Exception(evaluate)
import qualified OriginalSha512HashSpec as O
import Test.Hspec(hspec)
import System.Environment(getArgs,withArgs)
get o k = case K.lookup k o of Just v -> v;_ -> Null
chunk v=case fromJSON v of Success xs->B.pack(xs::[Word8]);_->B.empty
chunks o=case get o "chunks" of Array xs->map chunk(foldr(:)[]xs);_->[]
finish :: forall a. HashAlgorithm a => a -> Object -> Value
finish alg o=if get o "public"==Bool True then public alg ctx o else object["digest" .= B.unpack(A.convert(hashFinalize ctx)::B.ByteString)]
 where ctx=foldl hashUpdate(hashInitWith (if get o "witnessBottom"==Bool True then error "algorithm-bottom" else alg))(chunks o)
finishPrefix :: forall a. HashAlgorithmPrefix a => a -> Object -> Value
finishPrefix alg o=object["digest" .= B.unpack(A.convert(hashFinalizePrefix ctx (chunk(get o "end")) n)::B.ByteString)]
 where ctx=foldl hashUpdate(hashInitWith (if get o "witnessBottom"==Bool True then error "algorithm-bottom" else alg))(chunks o)
       n=case get o "prefix" of Number x->round x;_->0
public :: forall a. HashAlgorithm a => a -> Context a -> Object -> Value
public alg ctx o=object["digest" .= B.unpack raw,"show" .= show dig,"blockSize" .= hashBlockSize alg,"digestSize" .= hashDigestSize alg,"contextSize" .= A.length ctx,"context" .= B.unpack(B.take 208(A.convert ctx::B.ByteString)),"reads" .= parsed,"fromLengths" .= lengths,"equal" .= (dig==other),"compare" .= show(compare dig other),"witnessDigest" .= B.unpack(A.convert(hashWith (undefined::a) (B.concat(chunks o)))::B.ByteString)]
 where dig=hashFinalize ctx::Digest a
       raw=A.convert dig::B.ByteString
       text=case get o "read" of String v->show v;_->""
       input=case fromJSON(get o "read")of Success v->v;_->""
       parsed=[object["bytes" .= B.unpack(A.convert d::B.ByteString),"rest" .= rest]|(d,rest)<-(reads input::[(Digest a,String)])]
       other=case(reads input::[(Digest a,String)])of (d,_):_->d;_->dig
       lengths=[case(digestFromByteString(B.replicate n 0)::Maybe(Digest a))of Nothing->False;Just _->True|n<-[0,hashDigestSize alg-1,hashDigestSize alg,hashDigestSize alg+1]]
observe o=case get o "algorithm" of
 String "SHA384"->if K.member "prefix" o then finishPrefix SHA384 o else finish SHA384 o
 String "SHA512"->if K.member "prefix" o then finishPrefix SHA512 o else finish SHA512 o
 String "SHA512/224"->finish SHA512t_224 o
 _->finish SHA512t_256 o
sampleRows :: [Value]
sampleRows=concat[chunkRows name alg| (name,HashAlgBox alg)<-algs] ++ concat[prefixRows name alg family| (name,PrefixBox alg)<-pfx,family<-["prefix","hybrid"]]
 where
  algs=[("SHA384",HashAlgBox SHA384),("SHA512",HashAlgBox SHA512),("SHA512/224",HashAlgBox SHA512t_224),("SHA512/256",HashAlgBox SHA512t_256)]
  pfx=[("SHA384",PrefixBox SHA384),("SHA512",PrefixBox SHA512)]
  chunkRows :: HashAlgorithm a => String -> a -> [Value]
  chunkRows name alg=[row req (finish alg obj)|i<-[0..199],let (ck,U.ArbitraryBS0_2901 input)=unGen arbitrary(mkQCGen(1729+i))(i `mod` 101),let req=object["digest" .= True,"sampleFamily" .= ("chunk"::String),"algorithm" .= name,"chunks" .= map B.unpack(U.chunkS ck input)],let Object obj=req]
  prefixRows :: HashAlgorithmPrefix a => String -> a -> String -> [Value]
  prefixRows name alg family=[row req (finishPrefix alg obj)|i<-[0..199],let (U.ArbitraryBS0_2901 start,U.ArbitraryBS0_2901 end,U.Int0_2901 raw)=unGen arbitrary(mkQCGen(3729+i))(i `mod` 101),let n=if family=="hybrid"then unGen(choose(0,B.length end))(mkQCGen(5729+i))30 else raw,let req=object["digest" .= True,"sampleFamily" .= family,"algorithm" .= name,"chunks" .= [B.unpack(if family=="hybrid"then start else B.empty)],"end" .= B.unpack end,"prefix" .= n],let Object obj=req]
  row req expected=object["request" .= req,"expected" .= expected]
data HashAlgBox = forall a. HashAlgorithm a => HashAlgBox a
data PrefixBox = forall a. HashAlgorithmPrefix a => PrefixBox a
main=do
 args<-getArgs
 case args of
  ["--samples"]->mapM_ (L.putStrLn.encode) sampleRows
  "--suite":rest->withArgs rest(hspec O.spec)
  _->L.getContents>>=mapM_ run.L.lines
 where run line=case eitherDecode line of Left e->error e;Right o->L.putStrLn(encode(observe o))
