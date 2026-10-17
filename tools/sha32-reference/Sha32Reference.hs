{-# LANGUAGE ScopedTypeVariables, OverloadedStrings, ExistentialQuantification #-}
module Main where
import Crypto.Hash
import qualified Crypto.Hash.IO as IO
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
import Control.Exception(evaluate,try,SomeException,displayException)
import qualified OriginalSha32HashSpec as O
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
public alg ctx o=object["digest" .= B.unpack raw,"show" .= show dig,"blockSize" .= hashBlockSize alg,"digestSize" .= hashDigestSize alg,"contextSize" .= A.length ctx,"context" .= B.unpack(B.take (if hashDigestSize alg==20 then 96 else 168)(A.convert ctx::B.ByteString)),"reads" .= parsed,"fromLengths" .= lengths,"equal" .= (dig==other),"compare" .= show(compare dig other),"witnessDigest" .= B.unpack(A.convert(hashWith (undefined::a) (B.concat(chunks o)))::B.ByteString)]
 where dig=hashFinalize ctx::Digest a
       raw=A.convert dig::B.ByteString
       text=case get o "read" of String v->show v;_->""
       input=case fromJSON(get o "read")of Success v->v;_->""
       parsed=[object["bytes" .= B.unpack(A.convert d::B.ByteString),"rest" .= rest]|(d,rest)<-(reads input::[(Digest a,String)])]
       other=case(reads input::[(Digest a,String)])of (d,_):_->d;_->dig
       lengths=[case(digestFromByteString(B.replicate n 0)::Maybe(Digest a))of Nothing->False;Just _->True|n<-[0,hashDigestSize alg-1,hashDigestSize alg,hashDigestSize alg+1]]
observe o=case get o "algorithm" of
 String "SHA1"->if K.member "prefix" o then finishPrefix SHA1 o else finish SHA1 o
 String "SHA224"->if K.member "prefix" o then finishPrefix SHA224 o else finish SHA224 o
 _->if K.member "prefix" o then finishPrefix SHA256 o else finish SHA256 o
sampleRows :: [Value]
sampleRows=concat[chunkRows name alg| (name,HashAlgBox alg)<-algs] ++ concat[prefixRows name alg family| (name,PrefixBox alg)<-pfx,family<-["prefix","hybrid"]]
 where
  algs=[("SHA1",HashAlgBox SHA1),("SHA224",HashAlgBox SHA224),("SHA256",HashAlgBox SHA256)]
  pfx=[("SHA1",PrefixBox SHA1),("SHA224",PrefixBox SHA224),("SHA256",PrefixBox SHA256)]
  chunkRows :: HashAlgorithm a => String -> a -> [Value]
  chunkRows name alg=[row req (finish alg obj)|i<-[0..99],let (ck,U.ArbitraryBS0_2901 input)=unGen arbitrary(mkQCGen(1729+i))(i `mod` 101),let req=object["digest" .= True,"sampleFamily" .= ("chunk"::String),"algorithm" .= name,"chunks" .= map B.unpack(U.chunkS ck input)],let Object obj=req]
  prefixRows :: HashAlgorithmPrefix a => String -> a -> String -> [Value]
  prefixRows name alg family=[row req (finishPrefix alg obj)|i<-[0..99],let (U.ArbitraryBS0_2901 start,U.ArbitraryBS0_2901 end,U.Int0_2901 raw)=unGen arbitrary(mkQCGen(3729+i))(i `mod` 101),let n=if family=="hybrid"then unGen(choose(0,B.length end))(mkQCGen(5729+i))30 else raw,let req=object["digest" .= True,"sampleFamily" .= family,"algorithm" .= name,"chunks" .= [B.unpack(if family=="hybrid"then start else B.empty)],"end" .= B.unpack end,"prefix" .= n],let Object obj=req]
  row req expected=object["request" .= req,"expected" .= expected]
data HashAlgBox = forall a. HashAlgorithm a => HashAlgBox a
data PrefixBox = forall a. HashAlgorithmPrefix a => PrefixBox a
main=do
 args<-getArgs
 case args of
  ["--demand"]->mapM_ (L.putStrLn.encode) =<< demandRows
  ["--literals"]->mapM_ (L.putStrLn.encode) [object["request" .= object["digest" .= True,"algorithm" .= name,"chunks" .= [B.unpack input]],"expected" .= object["digest" .= B.unpack (B.pack (decodeHex (B.unpack expected))::B.ByteString)]] | (vs,groups)<-[(O.vectors,O.expected),(O.longVectors,O.expectedLong)],(name,O.HashAlg alg,results)<-groups,name `elem` ["SHA1","SHA224","SHA256"],(input,expected)<-zip vs results]
  ["--samples"]->mapM_ (L.putStrLn.encode) sampleRows
  "--suite":rest->withArgs rest(hspec O.spec)
  _->L.getContents>>=mapM_ run.L.lines
 where run line=case eitherDecode line of Left e->error e;Right o->if get o "mutable"==Bool True then mutableObserve o >>= L.putStrLn.encode else L.putStrLn(encode(observe o))

decodeHex []=[]
decodeHex (a:b:xs)=fromIntegral(digit a*16+digit b):decodeHex xs
 where digit c=if c>=48&&c<=57 then fromIntegral(c-48)else fromIntegral(c-87)

mutableObserve o=case get o "algorithm" of
 String "SHA1"->mutable SHA1 o
 String "SHA224"->mutable SHA224 o
 _->mutable SHA256 o
mutable :: forall a. HashAlgorithm a => a -> Object -> IO Value
mutable alg o=do
 ctx<-IO.hashMutableInitWith (if get o "witnessBottom"==Bool True then error "algorithm-bottom" else alg)
 mapM_ (IO.hashMutableUpdate ctx) (chunks o)
 a<-IO.hashMutableFinalize ctx
 b<-IO.hashMutableFinalize ctx
 IO.hashMutableReset ctx
 c<-IO.hashMutableFinalize ctx
 return $ object["first" .= B.unpack(A.convert a::B.ByteString),"second" .= B.unpack(A.convert b::B.ByteString),"reset" .= B.unpack(A.convert c::B.ByteString)]

demandRows=concat <$> sequence [demand SHA1 "SHA1",demand SHA224 "SHA224",demand SHA256 "SHA256"]
demand :: forall a. HashAlgorithmPrefix a => a -> String -> IO [Value]
demand alg name=mapM run probes
 where
  ctx=hashInitWith alg
  bad=error "context-bottom"::Context a
  bytes=error "bytes-bottom"::B.ByteString
  probes=[("ignored-witness",hashInitWith (undefined::a) `seq` 42),
    ("ignored-finalize",const 42(hashFinalize bad)),
    ("finalize-root",hashFinalize bad `seq` 42),
    ("empty-update-root",hashUpdate bad B.empty `seq` 42),
    ("update-bytes-first",hashUpdate bad bytes `seq` 42),
    ("updates-empty-tail",hashUpdates bad [B.empty,error "tail-bottom"] `seq` 42),
    ("updates-context-before-tail",hashUpdates bad [B.singleton 1,error "tail-bottom"] `seq` 42),
    ("updates-good-context-tail",hashUpdates ctx [B.singleton 1,error "tail-bottom"] `seq` 42),
    ("prefix-context-first",hashFinalizePrefix bad bytes (error "length-bottom") `seq` 42),
    ("prefix-bytes-first",hashFinalizePrefix ctx bytes (error "length-bottom") `seq` 42),
    ("prefix-length",hashFinalizePrefix ctx B.empty (error "length-bottom") `seq` 42),
    ("ignored-prefix",const 42(hashFinalizePrefix bad bytes (error "length-bottom")))]
  run (label,value)=do
   outcome<-try(evaluate value)::IO(Either SomeException Int)
   return $ object["algorithm" .= name,"case" .= label,"expected" .= either (takeWhile (/='\n').displayException) show outcome]
