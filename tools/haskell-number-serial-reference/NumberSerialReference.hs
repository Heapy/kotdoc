{-# LANGUAGE OverloadedStrings, ScopedTypeVariables #-}
module Main where
import qualified OriginalNumberSerialTests as Original
import qualified Crypto.Number.Basic as N
import qualified Crypto.Number.Serialize as BE
import qualified Crypto.Number.Serialize.LE as LE
import qualified Crypto.Number.Serialize.Internal as BI
import qualified Crypto.Number.Serialize.Internal.LE as LI
import Data.Aeson hiding(withArray)
import qualified Data.Aeson.KeyMap as K
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.Text as T
import Data.Word
import Data.Bits
import Foreign
import Control.Exception
import Control.Monad
import System.Environment(getArgs,withArgs)
import Test.Hspec(hspec)
import Test.QuickCheck hiding(Success,property)
import Test.QuickCheck.Gen(unGen)
import Test.QuickCheck.Random(mkQCGen)
import Utils

text o k = case K.lookup k o of Just(String s)->s;_->""
number (String s) | "bottom:" `T.isPrefixOf` s = error(T.unpack(T.drop 7 s))
                  | otherwise = read(T.unpack s) :: Integer
number _ = 0
arg o k = number(K.lookup k o `orElse` String "0")
 where orElse (Just x) _=x;orElse Nothing d=d
array o k = case K.lookup k o of
 Just v -> case fromJSON v of Success a->a;Error e->error e
 _ -> []
integer n=object["integer" .= show n]
pair part (a,b) = case part of
 "constructor" -> String "pair"
 "first" -> integer a
 "second" -> integer b
 _ -> object["pair" .= [show a,show b]]
triple part (a,b,g) = case part of
 "constructor" -> String "triple"
 "first" -> integer a
 "second" -> integer b
 "third" -> integer g
 _ -> object["triple" .= [show a,show b,show g]]
octets part bs = if part=="length" then object["length" .= B.length bs] else object["bytes" .= B.unpack bs]
maybeOctets part value = case value of
 Nothing -> object["maybe" .= ("none" :: T.Text)]
 Just bs | part=="constructor" -> object["maybe" .= ("some" :: T.Text)]
         | otherwise -> object["maybe" .= ("some" :: T.Text),"value" .= octets part bs]
property b=object["property" .= b]
observePublic o | part=="ignore" = String "ignored"
 | otherwise = case text o "op" of
 "numBits" -> integer(toInteger(N.numBits a))
 "numBytes" -> integer(toInteger(N.numBytes a))
 "log2" -> integer(toInteger(N.log2 a))
 "sqrti" -> pair part(N.sqrti a)
 "asPowerOf2AndOdd" -> case N.asPowerOf2AndOdd a of (e,n)->pair part(toInteger e,n)
 "gcde" -> triple part(N.gcde a b)
 "areEven" -> object["boolean" .= N.areEven (evenValues o)]
 "i2osp" -> octets part(write a)
 "i2ospOf" -> maybeOctets part(fixed len a)
 "i2ospOf_" -> octets part(fixedUnsafe len a)
 "os2ip" -> integer(readBytes(B.pack(array o "bytes")))
 "numberProperty" -> case text o "kind" of
  "num-bits" -> let i=fromInteger a :: Int in property(and[N.numBits(2^i-1)==i,N.numBits(2^i)==i+1,N.numBits(2^i+(2^i-1))==i+1])
  "num-bits2" -> property(not(a `testBit` N.numBits a) && a `testBit` (N.numBits a-1))
  "num-bytes" -> property(N.numBytes a==Original.byteCount a)
  "num-bytes-small" -> property(map N.numBytes [0,1,255,256,257,65535,65536]==[0,1,1,2,2,2,3])
  "as-power-of-2-and-odd" -> let(e,n)=N.asPowerOf2AndOdd a in property(a==2^e*n)
  "marshalling-be" -> property(a==BE.os2ip(BE.i2osp a :: B.ByteString))
  "marshalling-le" -> property(a==LE.os2ip(LE.i2osp a :: B.ByteString))
  "be-rev-le" -> property(a==LE.os2ip(B.reverse(BE.i2osp a :: B.ByteString)))
  "be-rev-le-40" -> property(a==LE.os2ip(B.reverse(BE.i2ospOf_ 40 a :: B.ByteString)))
  "le-rev-be" -> property(a==BE.os2ip(B.reverse(LE.i2osp a :: B.ByteString)))
  "le-rev-be-40" -> property(a==BE.os2ip(B.reverse(LE.i2ospOf_ 40 a :: B.ByteString)))
  _ -> error "unknown property"
 _ -> error "unknown operation"
 where
  a=arg o "a";b=arg o "b";part=text o "part";len=fromInteger(arg o "length")
  little=text o "endian"=="le"
  write=if little then LE.i2osp else BE.i2osp
  fixed=if little then LE.i2ospOf else BE.i2ospOf
  fixedUnsafe=if little then LE.i2ospOf_ else BE.i2ospOf_
  readBytes=if little then LE.os2ip else BE.os2ip
evenValues o = map number(array o "values") ++ if text o "tail"=="bottom" then error "tail" else []
observe(Object o)
 | text o "part"=="ignore" = return(String "ignored")
 | text o "op" `elem` ["rawWrite","rawWriteOf","rawRead"] = do
 let content=array o "bytes" :: [Word8];len=fromInteger(arg o "length");little=text o "endian"=="le"
 withArray([17,34,51]++content++[68,85,102,119]) $ \base -> do
  let ptr=base `plusPtr` 3
  if text o "op"=="rawRead" then do
   value<-(if little then LI.os2ip else BI.os2ip) ptr len
   after<-peekArray(length content+7)base
   return(object["integer" .= show value,"buffer" .= after])
  else do
   let write=case (little,text o "op"=="rawWriteOf") of (False,False)->BI.i2osp;(False,True)->BI.i2ospOf;(True,False)->LI.i2osp;(True,True)->LI.i2ospOf
   code<-write(arg o "a")ptr len
   after<-peekArray(length content+7)base
   return(object["ret" .= code,"buffer" .= after])
 | otherwise = return(observePublic o)
observe _ = error "object required"
vectors = forM_ Original.serializationVectors $ \(n,a,b) -> L.putStrLn(encode(object["length" .= n,"a" .= show a,"bytes" .= B.unpack b]))
samples = forM_ [0..127] $ \i -> do
 let Int1_2901 bits=unGen arbitrary(mkQCGen(1729+i))(i+1)
     Positive positive=unGen arbitrary(mkQCGen(7001+i))(i+1) :: Positive Integer
     QAInteger qa=unGen arbitrary(mkQCGen(17001+i))(i+1)
     signed=unGen arbitrary(mkQCGen(27001+i))(i+1) :: Integer
     emit kind a=L.putStrLn(encode(object["op" .= ("numberProperty" :: T.Text),"kind" .= (kind :: T.Text),"a" .= show a]))
 emit "num-bits" (toInteger bits)
 mapM_ (\kind->emit kind positive)["num-bits2","num-bytes"]
 emit "num-bytes-small" (0 :: Integer)
 emit "as-power-of-2-and-odd" signed
 mapM_ (\kind->emit kind qa)["marshalling-be","marshalling-le","be-rev-le","be-rev-le-40","le-rev-be","le-rev-be-40"]
main = do
 args<-getArgs
 case args of
  "--original":rest -> withArgs rest(hspec Original.spec)
  ["--vectors"] -> vectors
  ["--samples"] -> samples
  _ -> do
   input<-L.getContents
   forM_ (L.lines input) $ \line -> do
    result<-try $ do
     value<-observe(either error id(eitherDecode line))
     let encoded=encode value
     evaluate(L.length encoded) >> return encoded
    case result of
     Right v->L.putStrLn v
     Left(e :: SomeException)->L.putStrLn(encode(object["fatal" .= takeWhile(/='\n')(displayException e)]))
