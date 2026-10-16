{-# LANGUAGE OverloadedStrings, ScopedTypeVariables, ForeignFunctionInterface #-}
module Main where
import Crypto.Number.ModArithmetic
import qualified NumberSpec as Original
import qualified OriginalNumberTests as N
import Crypto.Number.Basic(numBits)
import Crypto.Number.Prime(generatePrime)
import Utils
import Test.QuickCheck hiding (Success,property)
import Test.QuickCheck.Gen(unGen)
import Test.QuickCheck.Random(mkQCGen)
import Data.Bits(testBit)
import Test.Hspec(hspec)
import Data.Aeson hiding (withArray)
import qualified Data.Aeson.KeyMap as K
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.Text as T
import Control.Exception
import Control.Monad
import Data.Word
import Foreign
import Foreign.C.Types
import System.Environment(getArgs,withArgs)

foreign import ccall unsafe "kotdoc_limb_op" limbOp :: Word32 -> Word32 -> Ptr Word64 -> Ptr Word64 -> Ptr Word64 -> Word64 -> Ptr Word64 -> Ptr Word64 -> Ptr Word64 -> IO ()
foreign import ccall unsafe "kotdoc_from_be" fromBE :: Ptr Word64 -> Word32 -> Ptr Word8 -> Word32 -> IO Word32
foreign import ccall unsafe "kotdoc_to_be" toBE :: Ptr Word8 -> Word32 -> Ptr Word64 -> Word32 -> IO ()
foreign import ccall unsafe "kotdoc_powm_audit" powAudit :: Ptr Word8 -> Ptr Word8 -> Word32 -> Ptr Word8 -> Word32 -> Ptr Word8 -> Word32 -> Ptr Word64 -> Word32 -> IO CInt

text o k = case K.lookup k o of Just (String s) -> s; _ -> "value"
arg o k = case K.lookup k o of
 Just (String s) | "bottom:" `T.isPrefixOf` s -> error (T.unpack (T.drop 7 s))
                 | otherwise -> read (T.unpack s) :: Integer
 _ -> 0
bytes o k = case K.lookup k o of
 Just v -> case fromJSON v of Success x -> x; Error e -> error e
 _ -> []
limbs o k n = case K.lookup k o of
 Just (Array _) -> map (fromInteger . read . T.unpack) (bytes o k :: [T.Text]) ++ repeat 0
 _ -> replicate n 0
integer n = object ["integer" .= show n]
maybeValue part v = case v of
 Nothing -> object ["maybe" .= ("none" :: T.Text)]
 Just x | part=="constructor" -> object ["maybe" .= ("some" :: T.Text)]
        | otherwise -> object ["maybe" .= ("some" :: T.Text),"value" .= show x]
observePublic o = if part=="ignore" then String "ignored" else case text o "op" of
 "expSafe" -> integer (expSafe g e m)
 "expFast" -> integer (expFast g e m)
 "inverse" -> maybeValue part (inverse g m)
 "inverseSafe" -> maybeValue part (inverseSafe g m)
 "inverseCoprimes" -> integer (inverseCoprimes g m)
 "inverseFermat" -> integer (inverseFermat g m)
 "jacobi" -> maybeValue part (jacobi g m)
 "squareRoot" -> maybeValue part (squareRoot m g)
 "numberProperty" -> case text o "kind" of
  "exponentiation/agrees with the fast one" -> property (expSafe g e m == expFast g e m)
  "exponentiation/agrees with the fast one on a modulus a key would have" -> property (expSafe g e m == expFast g e m)
  "exponentiation/agrees with the fast one on the corners" -> property (expSafe g e m == expFast g e m)
  "exponentiation/agrees with repetitive squaring on the corners" -> property (expSafe g e m == naivePow (g `mod` m) e m)
  "inverse/agrees with the plain inverse on the corners" -> property (inverseSafe g m == inverse g m)
  "inverse/agrees with the plain inverse" -> property (inverseSafe g m == inverse g m)
  "inverse/agrees with the plain inverse on a prime modulus" -> property (inverseSafe g m == inverse g m)
  "inverse/inverts" -> let g'=g `mod` m in if g'==0 then object ["discard" .= True] else property (fmap (\i -> g'*i `mod` m) (inverseSafe g' m) == Just 1)
  "squareRoot" -> let square x=(x*x) `mod` m; r=square <$> squareRoot m g in case jacobi g m of
    Just 1 -> property (Just g==r)
    Just (-1) -> property (Nothing==r)
    _ -> error "invalid jacobi result"
  _ -> error "unknown property"
 _ -> error "unknown operation"
 where g=arg o "g"; e=arg o "e"; m=arg o "m"; part=text o "part"

property b = object ["property" .= b]
naivePow b e m = foldl (\acc isSet -> acc*acc*(if isSet then b else 1) `mod` m) 1 bits
 where bits=[testBit e i | i<-[numBits e-1,numBits e-2..0]]

samples = do
 let request kind g e m = object ["op" .= ("numberProperty" :: T.Text),"kind" .= (kind :: T.Text),"g" .= show g,"e" .= show e,"m" .= show m]
 forM_ N.exponentiationCorners $ \(b,e,m) -> do
  L.putStrLn(encode(request "exponentiation/agrees with the fast one on the corners" b e m))
  when (e>=0 && m>1) $ L.putStrLn(encode(request "exponentiation/agrees with repetitive squaring on the corners" b e m))
 forM_ N.originalInverseCorners $ \(g,m) -> L.putStrLn(encode(request "inverse/agrees with the plain inverse on the corners" g (0 :: Integer) m))
 forM_ [0..127] $ \i -> do
  let (QAInteger g,QAInteger e,QAInteger m')=unGen arbitrary (mkQCGen (1729+i)) (i+1)
      m=abs m'+1; key=2*N.bigPrime*N.bigComposite+1
  forM_ [("exponentiation/agrees with the fast one",g,abs e,m),("exponentiation/agrees with the fast one on a modulus a key would have",g,abs e,key),("inverse/agrees with the plain inverse",g,0,m),("inverse/agrees with the plain inverse on a prime modulus",g,0,N.bigPrime),("inverse/inverts",g,0,N.bigPrime)] $ \(name,b,e,p) -> L.putStrLn(encode(request name b e p))
  let (drg,Int0_2901 bits')=unGen arbitrary (mkQCGen (7001+i)) (i+1)
      p=withTestDRG drg (generatePrime (5+bits' `mod` 500))
      a=unGen (choose (1,p-1)) (mkQCGen (17001+i)) (i+1)
  L.putStrLn(encode(request "squareRoot" a (0 :: Integer) p))

observeIO (Object o) = case text o "op" of
 "limb" -> do
  let n=fromInteger(arg o "n"); sentinel=0xa5a5a5a5a5a5a5a5; as=take (2*n) (limbs o "a" (2*n)); bs=take (2*n) (limbs o "b" (2*n)); ms=take n (limbs o "m" n)
  withArray as $ \a -> withArray bs $ \b -> withArray ms $ \m ->
   withArray (replicate (2*n) sentinel) $ \allocated -> withArray (replicate (2*n) sentinel) $ \t -> alloca $ \ret -> do
    let r=case text o "alias" of "a" -> a; "b" -> b; _ -> allocated
    limbOp (fromInteger(arg o "kind")) (fromIntegral n) a b m (fromInteger(arg o "scalar")) r t ret
    rr<-peekArray (2*n) r; tt<-peekArray (2*n) t; code<-peek ret
    return $ object ["limbs" .= map show rr,"scratch" .= map show tt,"ret" .= show code]
 "fromBE" -> do
  let n=fromInteger(arg o "n"); src=bytes o "bytes" :: [Word8]
  withArray (replicate n 0xa5a5a5a5a5a5a5a5) $ \r -> withArray src $ \s -> do
   code<-fromBE r (fromIntegral n) s (fromIntegral(length src)); rr<-peekArray n r
   return $ object ["limbs" .= map show rr,"ret" .= code]
 "toBE" -> do
  let n=fromInteger(arg o "n"); len=fromInteger(arg o "length"); as=take n(limbs o "a" n)
  withArray as $ \a -> allocaArray len $ \out -> do
   toBE out (fromIntegral len) a (fromIntegral n); result<-peekArray len out
   return $ object ["bytes" .= result]
 "powRaw" -> do
  let base=bytes o "base" :: [Word8]; expo=bytes o "exponent" :: [Word8]; modu=bytes o "modulus" :: [Word8]
  withArray base $ \b -> withArray expo $ \e -> withArray modu $ \m -> withArray (replicate(length modu) 165) $ \out -> allocaArray 6 $ \stats -> do
   code<-powAudit out b (fromIntegral(length base)) e (fromIntegral(length expo)) m (fromIntegral(length modu)) stats (fromInteger(arg o "allocationFailure"))
   result<-peekArray(length modu) out; ss<-peekArray 6 stats
   return $ object ["bytes" .= result,"ret" .= (fromIntegral code :: Int),"stats" .= map show ss]
 _ -> return (observePublic o)
observeIO _ = error "object required"

main = do
 args<-getArgs
 case args of
  "--original":rest -> withArgs rest (hspec Original.spec)
  ["--samples"] -> samples
  _ -> do
   input<-L.getContents
   forM_ (L.lines input) $ \line -> do
    outcome<-try $ do
     let request=either error id(eitherDecode line)
     value<-observeIO request
     let out=encode value
     evaluate (L.length out) >> return out
    case outcome of
     Right out -> L.putStrLn out
     Left (e :: SomeException) -> do
      let kind=case (fromException e :: Maybe CoprimesAssertionError,fromException e :: Maybe ModulusAssertionError) of
            (Just _,_) -> ["kind" .= ("coprime" :: T.Text)]
            (_,Just _) -> ["kind" .= ("modulus" :: T.Text)]
            _ -> []
      L.putStrLn(encode(object (["fatal" .= takeWhile (/='\n')(displayException e)]++kind)))
