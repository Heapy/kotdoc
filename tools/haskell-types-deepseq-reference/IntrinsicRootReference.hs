-- Original Control.DeepSeq and crypton instances are imported unchanged.
module Main where
import qualified Crypto.PubKey.RSA.Types as R
import Control.DeepSeq
import Control.Exception
newtype Ignore = Ignore Int
instance NFData Ignore where rnf _ = ()
bad :: R.KeyPair
bad=R.KeyPair(errorWithoutStackTrace "privateRoot")
partial :: R.KeyPair
partial=R.KeyPair(R.PrivateKey (errorWithoutStackTrace "publicField") 0 0 0 0 0 0)
observe name value=do
 r<-try(evaluate value)::IO(Either SomeException ())
 putStrLn(name++":"++either displayException(const "RETURNED")r)
main=do
 observe "root" (bad `seq` ())
 observe "rwhnf" (rwhnf bad)
 observe "force" (force bad `seq` ())
 observe "deepseq-result" ((() `deepseq` bad) `seq` ())
 observe "strictApply-result" (((\_->bad) $!! ()) `seq` ())
 observe "own-rnf" (rnf bad)
 observe "ignored-result" ((\_->()) (force bad))
 observe "record-lazy-root" (partial `seq` ())
 observe "record-lazy-rwhnf" (rwhnf partial)
 observe "record-lazy-rnf" (rnf partial)
 observe "force-ignored-dictionary" (force (Ignore(errorWithoutStackTrace "representedRoot")) `seq` ())
 observe "deepseq-ignored-input" (Ignore(errorWithoutStackTrace "unused") `deepseq` ())
