{-# LANGUAGE OverloadedStrings #-}
{-# LANGUAGE ScopedTypeVariables #-}
module Main where
import ControlledProbabilistic
import Crypto.Internal.Compat (unsafeDoIO)
import Crypto.Internal.ByteArray (ScrubbedBytes)
import qualified Data.ByteArray as BA
import qualified Crypto.Random as R
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import Data.IORef
import Data.Aeson
import System.Environment
import System.IO.Unsafe
import System.IO.Error
import System.Mem.StableName
import Control.Exception
import Control.Monad
import Control.DeepSeq
import Data.Word
counter=unsafePerformIO(newIORef(0::Int))
{-# NOINLINE counter #-}
secret=unsafeDoIO $ do
 args<-getArgs
 modifyIORef' counter(+1)
 n<-readIORef counter
 if head args=="success" || (head args=="recover" && n>1)
  then pure(BA.convert(B.pack[0..31]))
  else throwIO(userError "entropy failure")
{-# NOINLINE secret #-}
run name=case name of
 "pure-before"->B.singleton.fromIntegral $ probabilisticWith secret(undefined::B.ByteString)(pure(42::Int))
 "zero-before"->probabilisticWith secret(undefined::B.ByteString)(R.getRandomBytes 0)
 "ignored-before"->B.singleton.fromIntegral $ probabilisticWith secret(undefined::B.ByteString)((\(_::B.ByteString)->42::Int)<$>R.getRandomBytes 1)
 "first"->probabilisticWith secret B.empty(R.getRandomBytes 1)
 "different"->probabilisticWith secret(B.singleton 2)(R.getRandomBytes 1)
 "repeat"->probabilisticWith secret B.empty(R.getRandomBytes 33)
 "pure-after"->B.singleton.fromIntegral $ probabilisticWith secret(undefined::B.ByteString)(pure(42::Int))
 "zero-after"->probabilisticWith secret(undefined::B.ByteString)(R.getRandomBytes 0)
 "ignored-after"->B.singleton.fromIntegral $ probabilisticWith secret(undefined::B.ByteString)((\(_::B.ByteString)->42::Int)<$>R.getRandomBytes 1)
 "last"->probabilisticWith secret(B.pack[1..127])(R.getRandomBytes 65)
 _->error "op"
main=do
 firstError<-newIORef Nothing
 rows<-forM["pure-before","zero-before","ignored-before","first","different","repeat","pure-after","zero-after","ignored-after","last"] $ \name->do
  result<-try(evaluate(force(B.unpack(run name))))::IO(Either IOException [Word8])
  value<-case result of
   Right b->pure(object["bytes" .=b])
   Left e->do
    evaluated<-evaluate e;stable<-makeStableName evaluated;previous<-readIORef firstError
    case previous of Nothing->writeIORef firstError(Just stable);_->pure()
    pure(object["error" .=ioeGetErrorString e,"sameAsFirst" .=maybe True(==stable)previous])
  count<-readIORef counter
  pure(object["operation" .=name,"value" .=value,"entropyCalls" .=count])
 L.putStrLn(encode rows)
