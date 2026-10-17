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
import qualified OriginalRipemd160HashSpec as O
import Test.Hspec(hspec)
import System.Environment(getArgs,withArgs)
get o k = case K.lookup k o of Just v -> v;_ -> Null
chunk v=case fromJSON v of Success xs->B.pack(xs::[Word8]);_->B.empty
chunks o=case get o "chunks" of Array xs->map chunk(foldr(:)[]xs);_->[]
finish :: forall a. HashAlgorithm a => a -> Object -> Value
finish alg o=if get o "public"==Bool True then public alg ctx o else object["digest" .= B.unpack(A.convert(hashFinalize ctx)::B.ByteString)]
 where ctx=foldl hashUpdate(hashInitWith (if get o "witnessBottom"==Bool True then error "algorithm-bottom" else alg))(chunks o)
public :: forall a. HashAlgorithm a => a -> Context a -> Object -> Value
public alg ctx o=object["digest" .= B.unpack raw,"show" .= show dig,"blockSize" .= hashBlockSize alg,"digestSize" .= hashDigestSize alg,"contextSize" .= A.length ctx,"context" .= B.unpack(B.take 96(A.convert ctx::B.ByteString)),"reads" .= parsed,"fromLengths" .= lengths,"equal" .= (dig==other),"compare" .= show(compare dig other),"witnessDigest" .= B.unpack(A.convert(hashWith (undefined::a) (B.concat(chunks o)))::B.ByteString)]
 where dig=hashFinalize ctx::Digest a
       raw=A.convert dig::B.ByteString
       text=case get o "read" of String v->show v;_->""
       input=case fromJSON(get o "read")of Success v->v;_->""
       parsed=[object["bytes" .= B.unpack(A.convert d::B.ByteString),"rest" .= rest]|(d,rest)<-(reads input::[(Digest a,String)])]
       other=case(reads input::[(Digest a,String)])of (d,_):_->d;_->dig
       lengths=[case(digestFromByteString(B.replicate n 0)::Maybe(Digest a))of Nothing->False;Just _->True|n<-[0,hashDigestSize alg-1,hashDigestSize alg,hashDigestSize alg+1]]
observe o=finish RIPEMD160 o
sampleRows :: [Value]
sampleRows=concat[chunkRows name alg| (name,HashAlgBox alg)<-algs]
 where
  algs=[("RIPEMD160",HashAlgBox RIPEMD160)]
  chunkRows :: HashAlgorithm a => String -> a -> [Value]
  chunkRows name alg=[row req (finish alg obj)|i<-[0..99],let (ck,U.ArbitraryBS0_2901 input)=unGen arbitrary(mkQCGen(1729+i))(i `mod` 101),let req=object["digest" .= True,"sampleFamily" .= ("chunk"::String),"algorithm" .= name,"chunks" .= map B.unpack(U.chunkS ck input)],let Object obj=req]
  row req expected=object["request" .= req,"expected" .= expected]
data HashAlgBox = forall a. HashAlgorithm a => HashAlgBox a
main=do
 args<-getArgs
 case args of
  ["--demand"]->mapM_ (L.putStrLn.encode) =<< demandRows
  ["--literals"]->mapM_ (L.putStrLn.encode) [object["request" .= object["digest" .= True,"algorithm" .= name,"chunks" .= [B.unpack input]],"expected" .= object["digest" .= B.unpack (B.pack (decodeHex (B.unpack expected))::B.ByteString)]] | (vs,groups)<-[(O.vectors,O.expected),(O.longVectors,O.expectedLong)],(name,O.HashAlg alg,results)<-groups,name `elem` ["RIPEMD160"],(input,expected)<-zip vs results]
  ["--samples"]->mapM_ (L.putStrLn.encode) sampleRows
  "--suite":rest->withArgs rest(hspec O.spec)
  _->L.getContents>>=mapM_ run.L.lines
 where run line=case eitherDecode line of Left e->error e;Right o->if get o "mutable"==Bool True then mutableObserve o >>= L.putStrLn.encode else L.putStrLn(encode(observe o))

decodeHex []=[]
decodeHex (a:b:xs)=fromIntegral(digit a*16+digit b):decodeHex xs
 where digit c=if c>=48&&c<=57 then fromIntegral(c-48)else fromIntegral(c-87)

mutableObserve o=case get o "algorithm" of
 String "RIPEMD160"->mutable RIPEMD160 o
 String "SHA224"->mutable SHA224 o
 _->error "explicit RIPEMD160 algorithm required"
mutable :: forall a. HashAlgorithm a => a -> Object -> IO Value
mutable alg o=do
 ctx<-IO.hashMutableInitWith (if get o "witnessBottom"==Bool True then error "algorithm-bottom" else alg)
 mapM_ (IO.hashMutableUpdate ctx) (chunks o)
 a<-IO.hashMutableFinalize ctx
 b<-IO.hashMutableFinalize ctx
 IO.hashMutableReset ctx
 c<-IO.hashMutableFinalize ctx
 return $ object["first" .= B.unpack(A.convert a::B.ByteString),"second" .= B.unpack(A.convert b::B.ByteString),"reset" .= B.unpack(A.convert c::B.ByteString)]

demandRows=concat <$> sequence [demand RIPEMD160 "RIPEMD160"]
demand :: forall a. HashAlgorithm a => a -> String -> IO [Value]
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
    ("updates-good-context-tail",hashUpdates ctx [B.singleton 1,error "tail-bottom"] `seq` 42)]
  run (label,value)=do
   outcome<-try(evaluate value)::IO(Either SomeException Int)
   return $ object["algorithm" .= name,"case" .= label,"expected" .= either (takeWhile (/='\n').displayException) show outcome]
