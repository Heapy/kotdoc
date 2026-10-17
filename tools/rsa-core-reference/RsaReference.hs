{-# LANGUAGE OverloadedStrings #-}
{-# LANGUAGE ScopedTypeVariables #-}
module Main where
import qualified Crypto.PubKey.RSA as R
import qualified Crypto.PubKey.RSA.Prim as P
import qualified ObservedRsa as O
import qualified BoundedRsa as C
import qualified Utils as U
import Test.QuickCheck(arbitrary)
import Test.QuickCheck.Gen(unGen)
import Test.QuickCheck.Random(mkQCGen)
import qualified ObservedRsaPrim as I
import qualified RsaVectors as V
import qualified PubKey.RSASpec as Full
import qualified Crypto.Random as G
import qualified Crypto.Random.ChaChaDRG as D
import qualified PrimeProbabilistic as T
import qualified Data.ByteString as B
import qualified Data.ByteArray as BA
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Aeson
import Data.Aeson.Types
import Data.Word
import Data.IORef
import System.IO.Unsafe(unsafePerformIO)
import Control.Exception
import Control.DeepSeq
import System.IO
import System.Environment(getArgs,withArgs)
import Test.Hspec(hspec)
field o k = parseMaybe(.:k)o
text o k def = maybe def id(field o k)::String
number o k def = maybe def id(field o k)::Int
integer o k def = let s=text o k(show def)in if take 7 s=="bottom:"then error(drop 7 s)else read s
size o = let s=text o "size" "2"in if take 7 s=="bottom:"then error(drop 7 s)else read s
bytes o k = case text o (k<>"Bottom") ""of "" -> B.pack(maybe [] id(field o k)::[Word8]);label -> error label
pub o = R.PublicKey(size o)(integer o "n" 3233)(integer o "e" 17)
key o = R.PrivateKey(pub o)(integer o "d" 2753)(integer o "p" 61)(integer o "q" 53)(integer o "dP" 53)(integer o "dQ" 49)(integer o "qinv" 38)
blinder o = if text o "blinder" "none"=="bottom"then error "blinder"else if text o "blinder" "none"=="none"then Nothing else Just(R.Blinder(integer o "r" 2)(integer o "rinv" 1617))
traceRef :: IORef [(Int,[Word8])]
traceRef=unsafePerformIO(newIORef [])
{-# NOINLINE traceRef #-}
data Traced = Traced D.ChaChaDRG Int Int
instance G.DRG Traced where
 randomBytesGenerate n (Traced g count budget)=unsafePerformIO $ do
  if count>=budget then do modifyIORef' traceRef (++[(n,[])]);error "rng budget" else do
   let (b,next)=G.randomBytesGenerate n g
   modifyIORef' traceRef (++[(n,B.unpack(BA.convert b))])
   return(b,Traced next(count+1)budget)
seed o = case field o "words" :: Maybe [String] of
 Just [a,b,c,d,e] -> D.initializeWords(read a,read b,read c,read d,read e)
 _ -> D.initialize(B.pack[ fromIntegral((number o "seed" 0+i)`mod`256)|i<-[0..39]]::B.ByteString)
public p=object["size".=show(R.public_size p),"n".=show(R.public_n p),"e".=show(R.public_e p)]
private p=object["public".=public(R.private_pub p),"d".=show(R.private_d p),"p".=show(R.private_p p),"q".=show(R.private_q p),"dP".=show(R.private_dP p),"dQ".=show(R.private_dQ p),"qinv".=show(R.private_qinv p)]
viewKey o pair = case text o "part" "all"of
 "public" -> public(fst pair)
 "show" -> toJSON(show(snd pair))
 "d" -> toJSON(show(R.private_d(snd pair)))
 "qinv" -> toJSON(show(R.private_qinv(snd pair)))
 "constructor" -> String "pair"
 _ -> object["public".=public(fst pair),"private".=private(snd pair)]
viewMaybe o Nothing=String "Nothing"
viewMaybe o(Just pair)=if text o "part" "all"=="constructor"then String "Just"else viewKey o pair
viewBlinder (R.Blinder r inv)=object["r".=show r,"inverse".=show inv,"show".=show(R.Blinder r inv)]
randomResult o action = let
 (value,Traced final _ _)=G.withDRG(Traced(seed o)0(number o "budget" 10000))action
 (probe,_)=G.randomBytesGenerate 65 final::(B.ByteString,D.ChaChaDRG)
 in object["value".=value,"next".=B.unpack probe]
run o = case text o "op" ""of
 "ep" -> toJSON(B.unpack(P.ep(pub o)(bytes o "input")))
 "dp" -> toJSON(B.unpack(P.dp(blinder o)(key o)(bytes o "input")))
 "slow" -> toJSON(B.unpack(I.dpSlow(key o)(bytes o "input")))
 "fastNo" -> toJSON(B.unpack(I.dpFastNoBlinder(key o)(bytes o "input")))
 "fast" -> case blinder o of Just b->toJSON(B.unpack(I.dpFast b(key o)(bytes o "input")));_->error "blinder required"
 "multiply" -> toJSON(show(I.multiplication(integer o "a" 0)(integer o "b" 0)(integer o "m" 1)))
 "with" -> viewMaybe o(R.generateWith(integer o "p" 61,integer o "q" 53)(size o)(integer o "e" 17))
 "vector" -> let v=V.vectorsSHA1!!number o "index" 0 in viewKey o(V.vectorToPublic v,V.vectorToPrivate v)
 "boundedGenerate" -> randomResult o(viewKey o <$> C.generateBounded(number o "limit" 8)(size o)(integer o "e" 17))
 "generate" -> randomResult o(viewKey o <$> O.generate(size o)(integer o "e" 17))
 "blinder" -> randomResult o((if text o "part" "all"=="state"then const(String "ignored")else viewBlinder) <$>R.generateBlinder(integer o "n" 3233))
 _ -> error "request"
main = do
 args<-getArgs
 if args==["--samples"]then mapM_ sampleRequest[0..99] else if not(null args)&&head args=="--suite"then withArgs(tail args)(hspec Full.spec) else do
  hSetBuffering stdout LineBuffering
  contents<-L.getContents
  mapM_ dispatch(L.lines contents)
dispatch line=case eitherDecode line of
 Left e->error e
 Right o->do
  writeIORef traceRef[];T.resetTrace
  result<-try(evaluate(force(encode(run o))))::IO(Either SomeException L.ByteString)
  outer<-readIORef traceRef;inner<-T.readTrace
  let value=either(\e->object["error".=takeWhile(/='\n')(displayException e)])(\s->case eitherDecode s of Right v->v;Left e->error e)result
  L.putStrLn(encode(object["value".=value,"outer".=outer,"inner".=inner]))

sampleRequest index = do
 let U.TestDRG(a,b,c,d,e)=unGen arbitrary(mkQCGen(613+index))(index `mod` 101)
     v=head V.vectorsSHA1
 L.putStrLn(encode(object["op".=String "blinder","n".=show(V.n v),"words".=map show[a,b,c,d,e],"originalSample".=index]))
