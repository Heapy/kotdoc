{-# LANGUAGE GeneralizedNewtypeDeriving,OverloadedStrings,ScopedTypeVariables,ExistentialQuantification #-}
module Main where
import qualified Crypto.Number.Generate as G
import qualified ObservedNumberGenerate as OG
import Crypto.Random
import Crypto.Random.Types
import qualified Data.ByteArray as A
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Word
import Data.String(fromString)
import Data.IORef
import Data.Bits
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import qualified Data.Text as T
import Control.Monad.Trans.Reader
import Control.Monad.IO.Class
import Control.Exception
import Control.DeepSeq
import qualified OriginalNumberGenerateSpec as O
import qualified Utils as U
import Test.Hspec(hspec)
import Test.QuickCheck hiding(Success)
import Test.QuickCheck.Gen(unGen)
import Test.QuickCheck.Random(mkQCGen)
import System.Environment(getArgs,withArgs)
arg o k=case K.lookup k o of Just(String s)->T.unpack s;_->""
number o k=case K.lookup k o of Just(Number n)->round n;Just(String n)->read(T.unpack n);_->0
flag o k=K.lookup k o==Just(Bool True)
policy o=case arg o "top"of "highest"->Just G.SetHighest;"two"->Just G.SetTwoHighest;"bottom"->error "top-bottom";_->Nothing
integer o name=if flag o(fromString(name++"Bottom"))then error(name++"-bottom")else number o(fromString name)
data SomeBytes=forall a.A.ByteArrayAccess a=>SomeBytes a
data Provider=Provider(Int->IO B.ByteString)(IORef[Int])(IORef[[Word8]])(IORef[SomeBytes])
newtype Probe a=Probe{runProbe::ReaderT Provider IO a}deriving(Functor,Applicative,Monad)
keep :: forall a.A.ByteArray a=>B.ByteString->IORef[SomeBytes]->IO a
keep bytes ref=do let out=A.convert bytes::a
                  modifyIORef' ref(++[SomeBytes out]);pure out
instance MonadRandom Probe where
 getRandomBytes n=Probe$do
  Provider fetch requests payloads buffers<-ask
  liftIO$do modifyIORef' requests(++[n]);bytes<-fetch n;modifyIORef' payloads(++[B.unpack bytes]);keep bytes buffers
makeProvider o=do
 counter<-newIORef 0;requests<-newIORef[];payloads<-newIORef[];buffers<-newIORef[]
 let seed=case K.lookup "seed" o of
      Nothing->(0,0,0,0,0)
      Just v->case fromJSON v of Success([a,b,c,d,e]::[Word64])->(a,b,c,d,e);_->(0,0,0,0,0)
 state<-newIORef(drgNewTest seed)
 let fetch n=do
      i<-readIORef counter;writeIORef counter(i+1)
      if flag o "throw"||K.lookup "throwAt" o==Just(toJSON i)then throwIO(ErrorCall "rng-failure")else pure()
      case K.lookup "seed" o of
       Just _->do g<-readIORef state;let(bytes,g')=randomBytesGenerate n g::(B.ByteString,ChaChaDRG)
                  writeIORef state g';pure bytes
       Nothing->case K.lookup "chunks" o of
        Just v->case fromJSON v of
         Success(chunks::[[Word8]])->case drop i chunks of b:_->pure(B.pack b);[]->if flag o "exhaust"then throwIO(ErrorCall "rng-script-exhausted")else pure(B.pack(last chunks))
         _->error "chunks"
        Nothing->pure(B.replicate n(fromIntegral(number o "byte"::Int)))
 pure(Provider fetch requests payloads buffers)
action o=case arg o "op"of
 "params"->G.generateParams bits(policy o)oddValue
 "prefix"->G.generatePrefix bits
 "max"->G.generateMax(integer o "range")
 "between"->G.generateBetween(integer o "low")(integer o "high")
 _->error "operation"
 where bits=if flag o "bitsBottom"then error "bits-bottom"else number o "bits"
       oddValue=if flag o "oddBottom"then error "odd-bottom"else flag o "odd"
observe o
 |arg o "op"=="policy"=let Just a=policy o;Just b=policy(K.insert "top"(maybe Null id(K.lookup "other" o))o)in pure$object["show" .= showsPrec(number o "precedence")a "","equal" .= (a==b)]
 |otherwise=do
 provider@(Provider _ requests payloads buffers)<-makeProvider o
 result<-try(runReaderT(runProbe(action o))provider>>=evaluate.force)
 ns<-readIORef requests;bs<-readIORef payloads;stored<-readIORef buffers
 let snapshots=map(\(SomeBytes b)->B.unpack(A.convert b::B.ByteString))stored
 pure$object$["requests" .= ns,"payloads" .= bs,"inputBuffers" .= snapshots]++case result of Left(e::SomeException)->["exception" .= takeWhile(/='\n')(displayException e)];Right(n::Integer)->["value" .= show n]
originalSamples=mapM_ sample[(family,i)|family<-[0..3],i<-[0..199]] where
 sample(family,i)=do
  let seed=i*29+family;size=i`mod`101;drg@(U.TestDRG(a,b,c,d,e))=unGen arbitrary(mkQCGen seed)size;U.Int1_2901 bits=unGen arbitrary(mkQCGen(seed+1))size;Positive range=unGen arbitrary(mkQCGen(seed+2))size::Positive Integer
      (request,expected)=case family of
       0->(object["op" .= String "params","bits" .= bits,"top" .= String "highest"],U.withTestDRG drg(G.generateParams bits(Just G.SetHighest)False))
       1->(object["op" .= String "params","bits" .= (bits+1),"top" .= String "two"],U.withTestDRG drg(G.generateParams(bits+1)(Just G.SetTwoHighest)False))
       2->(object["op" .= String "params","bits" .= bits,"odd" .= True],U.withTestDRG drg(G.generateParams bits Nothing True))
       _->(object["op" .= String "max","range" .= show range],U.withTestDRG drg(G.generateMax range))
      Object base=request;full=K.insert "seed"(toJSON[a,b,c,d,e])base
  observed<-observe full
  case observed of Object v->if K.lookup "value" v==Just(toJSON(show expected))then pure()else error "loggedDRG differs from original withTestDRG";_->error "result"
  L.putStrLn$encode$object["group" .= String "original-generated-property-sample","family" .= family,"request" .= Object full,"expected" .= observed]
guardProbes=mapM_ run[(bits,top,oddValue,byte)|bits<-[1,2,8,9,17],top<-["none","highest","two"],oddValue<-[False,True],byte<-[0,127,128,255]]where
 run(bits,top,oddValue,byte)=do
  let Just(Object request)=decode(encode(object["op" .= String "params","bits" .= (bits::Int),"top" .= (top::String),"odd" .= oddValue,"byte" .= (byte::Int)]));policy=case top of "highest"->Just OG.SetHighest;"two"->Just OG.SetTwoHighest;_->Nothing
  provider<-makeProvider request
  observed<-runReaderT(runProbe(OG.generateParams bits policy oddValue))provider>>=evaluate
  ordinary<-observe request
  case ordinary of Object v->if K.lookup "value" v==Just(toJSON(show observed))then pure()else error "guarded numeric mismatch";_->error "result"
  (before,after)<-readIORef OG.observedFrames
  L.putStrLn$encode$object["bits" .= bits,"top" .= top,"odd" .= oddValue,"byte" .= byte,"value" .= show observed,"before" .= before,"after" .= after]
main=do
 args<-getArgs
 case args of
  "--original":rest->withArgs rest(hspec O.spec)
  ["--original-samples"]->originalSamples
  ["--guard-probes"]->guardProbes
  _->L.getContents>>=mapM_ run.L.lines
 where run line=case eitherDecode line of Left e->error e;Right o->observe o>>=L.putStrLn.encode
