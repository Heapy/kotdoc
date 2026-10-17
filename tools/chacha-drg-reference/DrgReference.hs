{-# LANGUAGE OverloadedStrings #-}
{-# LANGUAGE ScopedTypeVariables #-}
module Main where
import qualified Crypto.Random as R
import qualified Crypto.Random.ChaChaDRG as D
import qualified Crypto.Random.Probabilistic as P
import qualified ObservedProbabilistic as O
import qualified ControlledProbabilistic as C
import qualified InsecureRandom as I
import qualified Crypto.Hash as H
import Crypto.Error
import qualified Data.ByteArray as BA
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Aeson
import Data.Aeson.Types
import Data.Word
import Control.Exception
import Control.DeepSeq
import Control.Monad
import System.IO
import qualified Utils as U
import Test.QuickCheck(arbitrary)
import Test.QuickCheck.Gen(unGen)
import Test.QuickCheck.Random(mkQCGen)
field o k=parseMaybe(.:k)o
number o k d=maybe d id(field o k)::Int
text o k d=maybe d id(field o k)::String
bytes o k=B.pack(maybe [] id(field o k)::[Word8])
lengths o=maybe [] id(field o "lengths")::[Int]
wordSeed o=case map read(maybe [] id(field o "words")::[String])of [a,b,c,d,e]->D.initializeWords(a,b,c,d,e);_->error "words"
seed o=case text o "seedMode" "bytes" of
 "words"->wordSeed o
 "integer"->R.drgNewSeed(R.seedFromInteger(read(text o "integer" "0")))
 _->D.initialize(bytes o "seed")
draw :: R.DRG g=>Int->R.MonadPseudoRandom g B.ByteString
draw n=R.getRandomBytes n
action :: R.DRG g=>Object->R.MonadPseudoRandom g [[Word8]]
action o=case text o "action" "sequence" of
 "pure"->pure [[42]]
 "map"->(\b->[reverse(B.unpack b)]) <$>draw(number o "length" 17)
 "apply"->(\a b->[B.unpack(a<>b)]) <$>draw(number o "length" 17)<*>draw(number o "other" 33)
 "bind"->do a<-draw(number o "length" 1);b<-draw(if B.null a then 0 else fromIntegral(B.head a)`mod`65);pure[B.unpack a,B.unpack b]
 "ignoreMap"->(\_->[[42]]) <$>draw(number o "length" 17)
 _->map B.unpack <$>mapM draw(lengths o)
data TraceGen=TraceGen D.ChaChaDRG [Int]
instance R.DRG TraceGen where
 randomBytesGenerate n(TraceGen g tr)=let(b,next)=R.randomBytesGenerate n g in(b,TraceGen next(tr++[n]))
runTrace o=let (value,TraceGen final trace)=R.withDRG(TraceGen(seed o)[])(action o);(probe,_)=R.randomBytesGenerate 65 final::(B.ByteString,D.ChaChaDRG);(old,_)=R.randomBytesGenerate 65(seed o)::(B.ByteString,D.ChaChaDRG)in object["value" .=value,"trace" .=trace,"next" .=B.unpack probe,"previous" .=B.unpack old]
data Provider=Provider B.ByteString [Int]
instance R.DRG Provider where
 randomBytesGenerate n(Provider b trace)=(BA.convert b,Provider b(trace++[n]))
run o=case text o "mode" "drg" of
 "secret"->toJSON(B.unpack(BA.convert O.secret::B.ByteString))
 "samples"->toJSON[map show[a,b,c,d,e]|i<-[0..199],let U.TestDRG(a,b,c,d,e)=unGen arbitrary(mkQCGen(1729+i))(i`mod`101)]
 "seedInteger"->let s=R.seedFromInteger(read(text o "integer" "0"))in object["bytes" .=B.unpack(BA.convert s::B.ByteString),"integer" .=show(R.seedToInteger s)]
 "seedBinary"->case R.seedFromBinary(bytes o "seed")of CryptoFailed e->object["error" .=show e];CryptoPassed s->object["bytes" .=B.unpack(BA.convert s::B.ByteString),"integer" .=show(R.seedToInteger s)]
 "seedNew"->let (s,TraceGen final trace)=R.withDRG(TraceGen(seed o)[])(if field o "insecure"==Just True then (\s->BA.convert s::B.ByteString)<$>I.seedNew else (\s->BA.convert s::B.ByteString)<$>R.seedNew);(out,_)=R.randomBytesGenerate 65 final::(B.ByteString,D.ChaChaDRG)in object["bytes" .=B.unpack(BA.convert s::B.ByteString),"trace" .=trace,"next" .=B.unpack out]
 "drgNew"->let (g,TraceGen final trace)=R.withDRG(TraceGen(seed o)[])(if field o "insecure"==Just True then I.drgNew else R.drgNew);(out,_)=R.randomBytesGenerate 129 g::(B.ByteString,D.ChaChaDRG);(rest,_)=R.randomBytesGenerate 65 final::(B.ByteString,D.ChaChaDRG)in object["bytes" .=B.unpack out,"trace" .=trace,"next" .=B.unpack rest]
 "withRandomBytes"->let (value,TraceGen final trace)=R.withRandomBytes(TraceGen(seed o)[])(number o "length" 0)(\(b::B.ByteString)->if field o "ignore"==Just True then [42]else B.unpack(B.reverse b));(out,_)=R.randomBytesGenerate 65 final::(B.ByteString,D.ChaChaDRG)in object["value" .=value,"trace" .=trace,"next" .=B.unpack out]
 "seedProvider"->let (s,Provider _ trace)=R.withDRG(Provider(bytes o "entropy")[])(if field o "insecure"==Just True then (\s->BA.convert s::B.ByteString)<$>I.seedNew else (\s->BA.convert s::B.ByteString)<$>R.seedNew)in object["bytes" .=B.unpack(BA.convert s::B.ByteString),"trace" .=trace]
 "insecureSeedFormula"->let entropy=bytes o "entropy"in object["bytes" .=B.unpack(B.take 40(BA.convert(H.hashWith H.SHA512 entropy)::B.ByteString))]
 "probabilistic"->let material=bytes o "material";secret=bytes o "secret";controlled=C.probabilisticWith(BA.convert secret::BA.ScrubbedBytes)material(action o);ordinary=O.probabilisticFrom material(action o)in if field o "actual"==Just True && controlled/=ordinary then error "ordinary/controlled secret derivation mismatch"else toJSON controlled
 "demand"->case text o "name" "" of
  "prob-ignore"->toJSON(P.probabilisticFrom(undefined::B.ByteString)(pure(42::Int)))
  "prob-zero"->toJSON(B.unpack(P.probabilisticFrom(undefined::B.ByteString)(draw 0)))
  "drg-zero"->toJSON(B.unpack(fst(R.randomBytesGenerate 0(undefined::D.ChaChaDRG))::B.ByteString))
  "drg-negative"->toJSON(B.unpack(fst(R.randomBytesGenerate(-1)(undefined::D.ChaChaDRG))::B.ByteString))
  "with-ignore"->toJSON(fst(R.withRandomBytes(undefined::D.ChaChaDRG)17(\(_::B.ByteString)->42::Int)))
  "apply-ignore"->toJSON(fst(R.withDRG(undefined::D.ChaChaDRG)(pure(\_->42::Int)<*>draw 17)))
  "bind-ignore"->toJSON(fst(R.withDRG(undefined::D.ChaChaDRG)(draw 17 >>= \_ ->pure(42::Int))))
  "pure-ignore"->toJSON(fst(R.withDRG(undefined::D.ChaChaDRG)(pure(42::Int))))
  "map-ignore"->toJSON(fst(R.withDRG(undefined::D.ChaChaDRG)((\_->42::Int)<$>draw 17)))
  _->error "demand"
 _->runTrace o
main=do
 hSetBuffering stdout LineBuffering
 let loop=do eof<-isEOF;if eof then pure()else do
              line<-B.getLine
              case eitherDecodeStrict line of
               Left e->error e
               Right(Object o)->do r<-try(evaluate(force(run o)))::IO(Either SomeException Value);L.putStrLn(encode(case r of Left e->object["error" .=takeWhile(/='\n')(displayException e)];Right v->v));loop
 loop
