{-# LANGUAGE OverloadedStrings,DataKinds,TypeFamilies #-}
module Main where
import qualified ObservedPss as P
import Crypto.Hash
import Crypto.Hash.IO(HashAlgorithm(..))
import qualified Crypto.PubKey.RSA as R
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Aeson
import Data.Aeson.Types
import Data.String(fromString)
import Data.IORef
import System.IO.Unsafe
import Control.Exception
import Control.DeepSeq
field o k=parseMaybe(.:k)o
text o k d=maybe d id(field o k)::String
num o k d=maybe d id(field o k)::Int
{-# NOINLINE configuredSize #-}
configuredSize=unsafePerformIO(newIORef(20::Int))
data Probe=Probe
instance HashAlgorithm Probe where
 type HashBlockSize Probe=8
 type HashDigestSize Probe=4
 type HashInternalContextSize Probe=8
 hashBlockSize _=8
 hashDigestSize _=unsafePerformIO(readIORef configuredSize)
 hashInternalContextSize _=8
 hashInternalInit _=error "unexpected-hash"
 hashInternalUpdate _ _ _=error "unexpected-hash"
 hashInternalFinalize _ _=error "unexpected-hash"
run o=do
 writeIORef configuredSize(num o "hashLength"20)
 let bad :: String -> a -> a
     bad name v=if text o(fromString name)"no"=="bottom"then error name else v
     bits=bad "bits"(num o "bits"0)
     xs=bad "bytes"(maybe[]id(field o "input"))
     k=num o "length"128
     n=2^(num o "modulusBits"1024)-1
     key=R.PrivateKey(R.PublicKey k n 1)1 0 0 0 0 0
     salt=B.replicate(num o "saltBytes"0)7
     params=error "unused-params"::P.PSSParams Probe B.ByteString B.ByteString
     value=case text o "op""truncate"of
      "normalize"->toJSON(P.normalizeToKeySize bits xs)
      "guard"->case P.signWithSalt salt Nothing params key B.empty of Left e->toJSON(show e);Right _->String "Right"
      _->toJSON(P.emTruncate bits)
 r<-try(evaluate(force value))::IO(Either SomeException Value)
 return(either(\e->object["exception".=takeWhile(/='\n')(displayException e)])id r)
main=L.getContents>>=mapM_(\line->case eitherDecode line of Left e->error e;Right o->run o>>=L.putStrLn.encode).L.lines
