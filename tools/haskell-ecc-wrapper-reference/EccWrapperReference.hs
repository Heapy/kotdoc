{-# LANGUAGE OverloadedStrings, ScopedTypeVariables, ForeignFunctionInterface #-}
{-# OPTIONS_GHC -Wno-deprecations #-}
module Main where
import qualified ObservedECC as E
import qualified ObserverMemory as M
import qualified Crypto.PubKey.ECC.Types as C
import Crypto.Number.Basic(numBytes)
import qualified OriginalEccTests as Original
import qualified Data.Aeson.KeyMap as K
import Data.Aeson hiding(withArray)
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.Text as T
import Data.Word
import Data.IORef
import Data.Bits(xor)
import Control.Concurrent
import Foreign
import Foreign.C.Types
import Control.Exception
import Control.Monad
import System.Environment(getArgs,withArgs)
import Test.Hspec(hspec)

foreign import ccall unsafe "kotdoc_wrapper_reset" resetC :: CInt -> IO ()
foreign import ccall unsafe "kotdoc_wrapper_calls" callsC :: Ptr Word32 -> IO Word32
text o k=case K.lookup k o of Just(String s)->s;_->""
arg o k=case K.lookup k o of
 Just(String s) | "bottom:" `T.isPrefixOf` s -> error(T.unpack(T.drop 7 s))
                | otherwise -> read(T.unpack s) :: Integer
 _ -> 0
boolean o k=case K.lookup k o of Just(Bool x)->x;Just(String s)|"bottom:" `T.isPrefixOf` s->error(T.unpack(T.drop 7 s));_->False
array o k=case K.lookup k o of Just(Array vs)->foldr (:) [] vs;_->[]
point C.PointO=toJSON(["infinity"] :: [T.Text])
point(C.Point x y)=toJSON[show x,show y]
result part v=case v of
 E.MulInfinity->toJSON(["infinity"] :: [T.Text])
 E.MulUnsupported->toJSON(["unsupported"] :: [T.Text])
 E.MulPoint x y | part=="constructor"->String "point"
                | otherwise->toJSON[show x,show y]
field o=case text o "field" of
 "bottom"->error "field"
 "binary"->E.Binary(arg o "fx")(arg o "b")
 _->E.Prime(arg o "p")(arg o "a")(arg o "b")
lookupTable o=E.baseTable(arg o "p")(arg o "a")(arg o "b")(fromInteger(arg o "width"))(arg o "x")(arg o "y")
tableValue o table=case table of
 Nothing->return(object["maybe" .= ("none" :: T.Text)])
 Just fp->do
  h<-M.tableHash fp(fromIntegral(E.c_ecc_table_size(fromIntegral(numBytes(arg o "p")))(fromInteger(arg o "width"))))
  return(object["maybe" .= ("some" :: T.Text),"hash" .= h])
observe o | text o "part"=="ignore"=return(String "ignored")
 | text o "op"=="lookup"=case lookupTable o of
  Nothing->return(object["maybe" .= ("none" :: T.Text)])
  Just fp | text o "part"=="constructor"->return(object["maybe" .= ("some" :: T.Text)])
          | otherwise->tableValue o(Just fp)
 | text o "op"=="table"=case E.primeCurveTable p a b width x y of
  Nothing->return(object["maybe" .= ("none" :: T.Text)])
  Just fp | text o "part"=="tableOnly"->tableValue o(Just fp)
          | otherwise->return(result part(E.primeCurveTableMul fp p a b width k))
 | otherwise=return(result part v)
 where
  p=arg o "p";a=arg o "a";b=arg o "b";k=arg o "k";x=arg o "x";y=arg o "y";fx=arg o "fx"
  width=fromInteger(arg o "width");part=text o "part"
  v=case text o "op" of
   "prime"->E.primeCurveMul p a b width k x y
   "tableMulBottom"->E.primeCurveTableMul(error "table")p a b width k
   "binaryC"->E.binaryCurveC fx b width k x y
   "binaryH"->E.binaryCurveMul fx b width k x y
   "curve"->E.curveMul(field o)(arg o "order")k x y(boolean o "base")
   _->error "unknown operation"
capture o action=do
 M.reset(fromInteger(if K.member "poison" o then arg o "poison" else 165))
 resetC(fromInteger(if K.member "status" o then arg o "status" else 2147483647))
 outcome<-try $ do
  v<-action
  evaluate(L.length(encode v)) >> return v
 mem<-M.snapshot
 calls<-allocaArray 4096 $ \p->do n<-callsC p;peekArray(fromIntegral n)p
 let value=case outcome of Right v->v;Left(e :: SomeException)->object["fatal" .= takeWhile(/='\n')(displayException e)]
 return(object["value" .= value,"memory" .= mem,"calls" .= calls])
warm=forM_ ([minBound..maxBound] :: [C.CurveName]) $ \name->case C.getCurveByName name of
 C.CurveFP(C.CurvePrime p cc)->do
  let C.Point x y=C.ecc_g cc
  evaluate(E.baseTable p(C.ecc_a cc)(C.ecc_b cc)(numBytes(C.ecc_n cc))x y) >> return ()
 _->return ()
scenario o=do
 first<-newFirst
 values<-forM (array o "steps") $ \(Object step)->capture step $ do
  let op=text step "op"
  if op=="mutate" then case lookupTable step of
   Nothing->return Null
   Just fp->withForeignPtr fp $ \p->do
    let index=fromInteger(arg step "index")
    old<-peekByteOff p index :: IO Word8
    pokeByteOff p index(old `xor` fromInteger(arg step "xor"))
    return(String "mutated")
  else if op=="lookup" then do
   let table=lookupTable step
   evaluate table
   old<-readFirst first
   case old of Nothing->writeFirst first(Just table);_->return ()
   v<-if text step "part"=="constructor" then return(case table of Nothing->object["maybe" .= ("none" :: T.Text)];Just _->object["maybe" .= ("some" :: T.Text)]) else tableValue step table
   return(object["lookup" .= v,"sameAsFirst" .= maybe True (==table) old])
  else observe step
 return(toJSON values)
 where
  newFirst=newIORef Nothing
  readFirst=readIORef
  writeFirst=writeIORef
concurrent o=capture o $ do
 gate<-newEmptyMVar
 results<-replicateM(fromInteger(arg o "threads"))newEmptyMVar
 forM_ results $ \v->forkIO(readMVar gate >> evaluate(lookupTable o) >>= putMVar v)
 putMVar gate ()
 values<-mapM takeMVar results
 return(object["same" .= all(==head values)values,"some" .= case head values of Just _->True;_->False])
curves=forM_ ([minBound..maxBound] :: [C.CurveName]) $ \name->do
 let c=C.getCurveByName name;cc=C.common_curve c;C.Point x y=C.ecc_g cc;C.Point ox oy=Original.doubleAndAdd c 5(C.ecc_g cc)
     fields=case c of C.CurveFP(C.CurvePrime p _)->["field" .= ("prime" :: T.Text),"p" .= show p];C.CurveF2m(C.CurveBinary fx _)->["field" .= ("binary" :: T.Text),"fx" .= show fx]
 L.putStrLn(encode(object(fields++["curve" .= show name,"a" .= show(C.ecc_a cc),"b" .= show(C.ecc_b cc),"order" .= show(C.ecc_n cc),"x" .= show x,"y" .= show y,"ox" .= show ox,"oy" .= show oy,"width" .= show(numBytes(C.ecc_n cc))])))
run o=do
 value<-capture o(observe o)
 if boolean o "mathCheck" then do
  let name=head[n|n<-[minBound..maxBound] :: [C.CurveName],T.pack(show n)==text o "curve"]
      math=point(Original.doubleAndAdd(C.getCurveByName name)(arg o "k")(C.Point(arg o "x")(arg o "y")))
  return(object["observed" .= value,"math" .= math])
 else return value
main=do
 args<-getArgs
 case args of
  ["--curves"]->curves
  "--original":rest->withArgs rest(hspec Original.spec)
  _->do
   when(args==["--warm"])warm
   input<-L.getContents
   forM_ (L.lines input) $ \line->do
    let Object o=either error id(eitherDecode line)
    value<-case text o "op" of "cacheScenario"->scenario o;"cacheConcurrent"->concurrent o;_->run o
    L.putStrLn(encode value)
