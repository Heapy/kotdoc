{-# LANGUAGE ForeignFunctionInterface, OverloadedStrings, ScopedTypeVariables #-}
module Main where
import qualified OriginalEccTests as Original
import qualified Crypto.PubKey.ECC.Types as E
import qualified Crypto.PubKey.ECC.Prim as P
import qualified Crypto.Number.Serialize as BE
import Crypto.Number.Basic(numBytes,numBits)
import Crypto.Hash(SHA256,Digest,hash)
import Data.Aeson hiding(withArray)
import qualified Data.Aeson.KeyMap as K
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.Text as T
import Data.Word
import Data.Bits(xor)
import Foreign
import Foreign.C.Types
import Control.Exception
import Control.Monad
import System.Environment(getArgs,withArgs)
import Test.Hspec(hspec)

foreign import ccall unsafe "kotdoc_source_ecc_table_size" tableSize :: Word32 -> Word32 -> Word32
foreign import ccall safe "kotdoc_source_ecc_mul" multiplyC :: Ptr Word8 -> Ptr Word8 -> Ptr Word8 -> Ptr Word8 -> Ptr Word8 -> Word32 -> Ptr Word8 -> Ptr Word8 -> Ptr Word8 -> Word32 -> IO CInt
foreign import ccall safe "kotdoc_source_ecc_table_build" buildC :: Ptr Word8 -> Ptr Word8 -> Ptr Word8 -> Word32 -> Ptr Word8 -> Ptr Word8 -> Ptr Word8 -> Word32 -> IO CInt
foreign import ccall safe "kotdoc_source_ecc_table_mul" tableC :: Ptr Word8 -> Ptr Word8 -> Ptr Word8 -> Ptr Word8 -> Word32 -> Ptr Word8 -> Ptr Word8 -> Ptr Word8 -> Word32 -> IO CInt
foreign import ccall unsafe "kotdoc_prime_reset" reset :: Word32 -> IO ()
foreign import ccall unsafe "kotdoc_prime_stats" statsC :: Ptr Word64 -> Ptr Word64 -> Ptr Word64 -> Ptr Word64 -> Ptr Word64 -> Ptr Word32 -> IO ()

arg o k = case K.lookup k o of Just (String s) -> read(T.unpack s) :: Integer; _ -> 0
text o k = case K.lookup k o of Just (String s) -> s; _ -> ""
array o k = case K.lookup k o of
 Just v -> case fromJSON v of Success a -> a; Error e -> error e
 _ -> []
data Buffer = Buffer (Ptr Word8) Int
ptr (Buffer p _) = p `plusPtr` 3
full (Buffer p n) = peekArray (n+7) p
buffer bs action = withArray ([17,34,51]++bs++[68,85,102,119]) $ \p -> action(Buffer p(length bs))
stats = allocaArray 10 $ \ops -> alloca $ \h -> allocaArray 16 $ \sizes -> allocaArray 16 $ \freed -> allocaArray 16 $ \cleared -> allocaArray 2 $ \counts -> do
 statsC ops h sizes freed cleared counts
 [n,f]<-peekArray 2 counts
 os<-peekArray 10 ops;hh<-peek h;ss<-peekArray(fromIntegral n)sizes;fs<-peekArray(fromIntegral f)freed;cs<-peekArray(fromIntegral f)cleared
 return $ object ["ops" .= map show os,"hash" .= show hh,"allocations" .= map show ss,"frees" .= fs,"cleared" .= cs]
tableHash p n = do bs<-B.packCStringLen(castPtr p,n);return(show(hash bs :: Digest SHA256))
point E.PointO = toJSON(["infinity"] :: [T.Text])
point (E.Point x y) = toJSON [show x,show y]

observe (Object o)
 | text o "op"=="size" = return(object["size" .= show(tableSize(fromInteger(arg o "plen"))(fromInteger(arg o "klen")))])
 | otherwise = do
 let pbytes=array o "p" :: [Word8];len=length pbytes;kbytes=array o "k" :: [Word8]
 buffer pbytes $ \p -> buffer (array o "a") $ \a -> buffer (array o "b") $ \b -> buffer (array o "x") $ \x -> buffer (array o "y") $ \y -> buffer kbytes $ \k -> buffer(replicate len 165) $ \allocatedX -> buffer(replicate len 165) $ \allocatedY -> do
  let outx=if text o "alias"=="point" then x else allocatedX
      outy=case text o "alias" of "point" -> y; "same" -> outx; _ -> allocatedY
      result code = do
       sx<-full outx;sy<-full outy;ss<-stats
       ins<-mapM (\(name,buf) -> do v<-full buf;return(name,toJSON v)) [("p",p),("a",a),("b",b),("x",x),("y",y),("k",k)]
       return $ object ["ret" .= (fromIntegral code :: Int),"x" .= sx,"y" .= sy,"stats" .= ss,"inputs" .= Object(K.fromList ins)]
  outcome <- if text o "op"=="mul" then do
   reset(fromInteger(arg o "fail"))
   code<-multiplyC(ptr outx)(ptr outy)(ptr x)(ptr y)(ptr k)(fromIntegral(length kbytes))(ptr a)(ptr b)(ptr p)(fromIntegral len)
   result code
  else do
   let size=fromIntegral(tableSize(fromIntegral len)(fromIntegral(length kbytes)))
       words=size `div` 8;sentinel=0xa5a5a5a5a5a5a5a5 :: Word64
   withArray(replicate (words+4) sentinel) $ \base -> do
    let tab=castPtr(base `plusPtr` 16)
    reset(fromInteger(arg o "buildFail"))
    built<-buildC tab(ptr x)(ptr y)(fromIntegral(length kbytes))(ptr a)(ptr b)(ptr p)(fromIntegral len)
    buildStats<-stats;originalHash<-tableHash tab size
    when(built==0) $ do
     forM_ (array o "mutations" :: [[Int]]) $ \[index,value] -> do
      old<-peekByteOff tab index :: IO Word8;pokeByteOff tab index (old `xor` fromIntegral value)
     when(text o "tableOverride"=="zero") $ forM_ [0..size-1] $ \i -> pokeByteOff tab i (0 :: Word8)
    before<-tableHash tab size
    reset(fromInteger(arg o "fail"))
    mul<-if built==0 then tableC(ptr outx)(ptr outy)tab(ptr k)(fromIntegral(length kbytes))(ptr a)(ptr b)(ptr p)(fromIntegral len) >>= result else return Null
    after<-tableHash tab size;guards<-mapM(peekElemOff base)[0,1,words+2,words+3]
    return $ object ["build" .= (fromIntegral built :: Int),"buildStats" .= buildStats,"builtHash" .= originalHash,"tableHash" .= before,"tableHashAfter" .= after,"guards" .= map show guards,"result" .= mul]
  let check=case K.lookup "mathCheck" o of Just(Bool b)->b;_->False
  if check then do
   let name=head[n | n<-[minBound..maxBound] :: [E.CurveName],T.pack(show n)==text o "curve"]
       c=E.getCurveByName name
       scalar=BE.os2ip(B.pack kbytes)
       q=E.Point(BE.os2ip(B.pack(array o "x")))(BE.os2ip(B.pack(array o "y")))
   return $ object ["native" .= outcome,"math" .= point(Original.doubleAndAdd c scalar q)]
  else return outcome
observe _ = error "object required"

curves = forM_ ([minBound..maxBound] :: [E.CurveName]) $ \name -> case E.getCurveByName name of
 E.CurveFP(E.CurvePrime p cc) -> do
  let E.Point gx gy=E.ecc_g cc; E.Point ox oy=P.pointMul(E.getCurveByName name)5(E.ecc_g cc)
  L.putStrLn(encode(object["name" .= show name,"p" .= show p,"a" .= show(E.ecc_a cc),"b" .= show(E.ecc_b cc),"n" .= show(E.ecc_n cc),"gx" .= show gx,"gy" .= show gy,"ox" .= show ox,"oy" .= show oy]))
 _ -> return ()
weights = forM_ [E.SEC_p384r1,E.SEC_p521r1] $ \name -> do
 let n=E.ecc_n(E.common_curve(E.getCurveByName name))
 L.putStrLn(encode(object["name" .= show name,"scalars" .= map show(Original.originalWeightScalars n),"pairs" .= map(\(a,b)->[show a,show b])(Original.originalWeightPairs n)]))
main = do
 args<-getArgs
 case args of
  "--original":rest -> withArgs rest(hspec Original.spec)
  ["--curves"] -> curves
  ["--weights"] -> weights
  _ -> do
   input<-L.getContents
   forM_ (L.lines input) $ \l -> do
    outcome<-try $ do
     value<-observe(either error id(eitherDecode l))
     let encoded=encode value
     evaluate(L.length encoded) >> return encoded
    case outcome of
     Right v -> L.putStrLn v
     Left(e :: SomeException) -> L.putStrLn(encode(object["fatal" .= takeWhile(/='\n')(displayException e)]))
