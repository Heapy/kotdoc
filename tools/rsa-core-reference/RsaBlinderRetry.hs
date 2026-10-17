{-# LANGUAGE OverloadedStrings #-}
module Main where
import qualified Crypto.Random as G
import qualified Crypto.PubKey.RSA as R
import qualified Data.ByteArray as B
import qualified Data.ByteString as S
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Aeson
import Data.IORef
import Control.Exception
import Control.DeepSeq
import System.IO.Unsafe(unsafePerformIO)
data Probe=Probe String Int deriving Show
calls :: IORef [Int]
calls=unsafePerformIO(newIORef [])
{-# NOINLINE calls #-}
instance G.DRG Probe where
 randomBytesGenerate n (Probe mode i)=unsafePerformIO $ do
  modifyIORef' calls(n:)
  if mode=="rootFault"&&i==50 then error "pair fault"else return
   (if mode=="bytesFault"&&i==50 then error "bytes fault"else B.convert(S.replicate n(if mode=="reject"||i<99 then 255 else 1)),
    if mode=="stateFault"&&i==99 then error "state fault"else Probe mode(i+1))
run mode n projection=do
 writeIORef calls[]
 let action=R.generateBlinder n
     pair=G.withDRG(Probe mode 0)action
     root=fst pair `seq` "42"
     expressions=case projection of
      "ignore"->[show(fst(G.withDRG(Probe mode 0)(fmap(const(42::Int))action)))]
      "root"->[root]
      "state"->[show(snd pair)]
      "repeatRootState"->[root,root,show(snd pair)]
      _->error "projection"
 results<-mapM capture expressions
 L.putStrLn(encode(object["mode".=mode,"n".=show n,"projection".=projection,"results".=results]))
 where capture expression=do
        value<-try(evaluate(force expression))::IO(Either SomeException String)
        trace<-reverse <$>readIORef calls
        return(object["value".=either(takeWhile(/='\n').displayException)id value,"calls".=trace])
main=mapM_(\(m,n,p)->run m n p)[(m,n,p)|m<-["reject","success","rootFault","bytesFault","stateFault"],n<-[127,129,500],p<-["ignore","root","state","repeatRootState"]]
