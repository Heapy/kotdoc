{-# LANGUAGE OverloadedStrings #-}
{-# LANGUAGE ScopedTypeVariables #-}
module Main where
import qualified Crypto.Random as R
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.ByteArray as BA
import Data.Aeson
import Data.Aeson.Types
import Data.IORef
import Control.Exception
import Control.Monad
import System.IO
import System.IO.Unsafe

data Custom=Custom String (IORef [String]) Int
mark ref event value=unsafePerformIO(modifyIORef' ref(++[event])>>pure value)
{-# NOINLINE mark #-}
generateCustom n (Custom mode ref token)=mark ref("pair:"++show n) $ case mode of
 "root"->error "pair demanded"
 _->(mark ref "value"(if mode=="value"then error "value demanded"else BA.convert(B.pack[1,2])),mark ref "state"(if mode=="state"then error "state demanded"else Custom mode ref(token+n)))
{-# NOINLINE generateCustom #-}
instance R.DRG Custom where randomBytesGenerate=generateCustom
draw n=R.getRandomBytes n::R.MonadPseudoRandom Custom B.ByteString
badAction ref=mark ref "action"(error "action demanded")::R.MonadPseudoRandom Custom B.ByteString
makePair op gen@(Custom _ ref _) =case op of
 "withCallbackGood"->R.withRandomBytes gen 7(\(_::B.ByteString)->mark ref "mapper"42)
 "withCallbackBad"->R.withRandomBytes gen 7(\(_::B.ByteString)->mark ref "mapper"(error "mapper demanded"))
 "mapCallbackGood"->R.withDRG gen((\_->mark ref "mapper"42)<$>draw 7)
 "mapCallbackBad"->R.withDRG gen((\_->mark ref "mapper"(error "mapper demanded"))<$>draw 7)
 "applyCallbackBad"->R.withDRG gen(pure(\_->mark ref "mapper"(error "mapper demanded"))<*>draw 7)
 "bindCallbackGood"->R.withDRG gen(draw 7 >>= \_->mark ref "binder"(pure 42))
 "withIgnore"->R.withRandomBytes gen 7(\(_::B.ByteString)->42)
 "withDemand"->R.withRandomBytes gen 7(\(b::B.ByteString)->B.length b)
 "get"->R.withDRG gen(B.length <$>draw 7)
 "mapIgnore"->R.withDRG gen((\_->42)<$>draw 7)
 "mapDemand"->R.withDRG gen(B.length <$>draw 7)
 "applyIgnore"->R.withDRG gen(pure(\_->42)<*>draw 7)
 "applyDemand"->R.withDRG gen(pure B.length<*>draw 7)
 "applyTwoIgnore"->R.withDRG gen(((\_->(\_->42))<$>draw 3)<*>draw 7)
 "applyFunctionBad"->R.withDRG gen(pure(error "function demanded")<*>draw 7)
 "bindIgnore"->R.withDRG gen(draw 7 >>= \_->pure 42)
 "bindLazyDemand"->R.withDRG gen(draw 7 >>= \b->pure(B.length b))
 "bindStrictDemand"->R.withDRG gen(draw 7 >>= \b->B.length b `seq` pure(B.length b))
 "bindTwoIgnore"->R.withDRG gen(draw 3 >>= \_->(\_->42)<$>draw 7)
 "bindFunctionBad"->R.withDRG gen(draw 7 >>= \_->mark ref "bind"(error "bind demanded"))
 "mapActionIgnore"->R.withDRG gen((\_->42)<$>badAction ref)
 "mapActionDemand"->R.withDRG gen(B.length <$>badAction ref)
 "bindActionIgnore"->R.withDRG gen(badAction ref >>= \_->pure 42)
 "applyArgumentRoot"->R.withDRG gen(pure(\_->42)<*>badAction ref)
 "pure"->R.withDRG gen(pure 42)
 _->error "op"
get o k=case parseMaybe(.:k)o of Just v->v;_->error "field"
run o=do
 ref<-newIORef[]
 let pair=makePair(get o "operation")(Custom(get o "mode")ref 0)
 rows<-forM(get o "projections"::[String]) $ \projection->do
  result<-try(evaluate(if projection=="value"then fst pair else case snd pair of Custom _ _ token->token))::IO(Either SomeException Int)
  events<-readIORef ref
  pure(object["result" .=either(takeWhile(/='\n').displayException)show result,"events" .=events])
 pure(toJSON rows)
main=do
 hSetBuffering stdout LineBuffering
 let loop=do eof<-isEOF;if eof then pure()else do
              line<-B.getLine
              case eitherDecodeStrict line of Left e->error e;Right(Object o)->run o>>=L.putStrLn.encode
              loop
 loop
