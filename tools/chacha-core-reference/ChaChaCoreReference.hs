{-# LANGUAGE OverloadedStrings #-}
module Main where
import qualified Crypto.Cipher.ChaCha as C
import qualified OriginalChaChaCoreSpec as O
import qualified ChaChaCoreVectors as V
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import Data.Word
import Test.Hspec(hspec)
import Test.QuickCheck(arbitrary)
import Test.QuickCheck.Gen(unGen)
import Test.QuickCheck.Random(mkQCGen)
import qualified Utils as U
import System.Environment(getArgs,withArgs)
row request output=object["request" .= request,"expected" .= object["bytes" .= B.unpack(output::B.ByteString)]]
request rounds key iv counter op input sizes=object["haskell" .= True,"rounds" .= (rounds::Int),"key" .= B.unpack key,"iv" .= B.unpack iv,"counter" .= (counter::Word32),"operation" .= (op::String),"bytes" .= B.unpack input,"sizes" .= (sizes::[Int])]
run rounds key iv counter op input sizes=B.concat(fst(go input sizes (C.setCounter32 counter(C.initialize rounds key iv))))
 where
  go remaining [] state=([B.empty],state)
  go remaining (n:ns) state=
    let data'=B.take n remaining
        (output,next)=if op=="combine" then C.combine state data' else C.generate state n
        (rest,lastState)=go(B.drop n remaining)ns next
    in (output:rest,lastState)

shorts=[(8,16,V.b8_128_k0_i0),(12,16,V.b12_128_k0_i0),(20,16,V.b20_128_k0_i0),(8,32,V.b8_256_k0_i0),(12,32,V.b12_256_k0_i0),(20,32,V.b20_256_k0_i0)]
vectors=[row(request r(B.replicate k 0)(B.replicate 8 0)0 "generate" B.empty[B.length expected]) expected|(r,k,expected)<-shorts]++[let req=object["haskell" .= True,"xchacha" .= True,"rounds" .= r,"key" .= B.unpack key,"iv" .= B.unpack iv,"operation" .= ("combine"::String),"bytes" .= B.unpack input,"sizes" .= [B.length input]]in row req expected|let(r,key,iv,input,expected)=V.xChaCha20_ExampleKAT]++[row(request r key iv counter "combine" input[B.length input])expected|(r,key,iv,input,expected,counter)<-[V.rfc8439A2_1,V.rfc8439A2_2,V.rfc8439A2_3]]++[object["request" .= (case request 20 O.longKey O.longNonce counter "generate" B.empty[len] of Object m->Object(K.insert "sha256"(Bool True)m)),"expected" .= object["bytes" .= B.unpack(fst(C.generate(C.setCounter32 counter(C.initialize 20 O.longKey O.longNonce))len)::B.ByteString),"sha256" .= B.unpack digest]]|(counter,len,digest)<-O.longVectors]
carryRows=[row(request 20 O.longKey O.longNonce counter "combine"(B.replicate 8192 0)(replicate 128 64))(fst(C.combine(C.setCounter32 counter(C.initialize 20 O.longKey O.longNonce))(B.replicate 8192 0))::B.ByteString)|counter<-[0xffffff00,0xfffffff0,0xfffffffe]]
samples=[row req output|i<-[0..199],family<-[0..2],let (O.Vector rounds key iv,U.ChunkingLen ck,U.ChunkingLen0_127 ck0,U.Int0_2901 count,U.ArbitraryBS0_2901 input)=unGen arbitrary(mkQCGen(1729+i))(i `mod` 101),let total=if family==0 then 1048 else if family==1 then count else B.length input,let plan=if family==0 then ck else ck0,let sizes=takeSizes total(cycle plan),let op=if family==2 then "combine"else "generate",let req0=request rounds key iv 0 op input sizes,let req=case req0 of Object m->Object(K.insert "family"(toJSON(family::Int))m),let output=run rounds key iv 0 op input sizes]
 where takeSizes 0 _=[];takeSizes n(x:xs)=min x n:takeSizes(n-min x n)xs
main=do
 args<-getArgs
 case args of
  "--suite":rest->withArgs rest(hspec O.spec)
  ["--vectors"]->mapM_(L.putStrLn.encode)(vectors++carryRows)
  ["--samples"]->mapM_(L.putStrLn.encode)samples
  _->error "mode"
