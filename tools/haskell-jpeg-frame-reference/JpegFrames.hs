{-# LANGUAGE CPP #-}
{-# LANGUAGE ScopedTypeVariables #-}
module Main where
import Codec.Picture.Jpg.Internal.Types
import Codec.Picture.Tiff.Internal.Types
import Data.Binary (Binary(..),encode)
import Data.Binary.Get (Get,runGetOrFail,runGet)
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy as L
import qualified Data.Vector.Storable as VS
import Control.Monad.ST (runST)
import Control.Exception (SomeException,try,evaluate,displayException)
import Control.DeepSeq (force)
import Data.Int (Int64)
import Data.List (find)
import Numeric (readHex)
import System.Environment (getArgs)
import System.Info (compilerName,compilerVersion)
import Data.Version (showVersion)

type Part=(String,Int,[Int],[Int])
type Request=(String,[Int64],String,[Part])
data Result=Result [(String,String)] (Maybe String) (Maybe L.ByteString)
obj xs="{"++commas [show k++":"++v | (k,v)<-xs]++"}"
commas []="";commas [x]=x;commas (x:xs)=x++","++commas xs
packed=show . L.unpack
suffix b="["++show(L.length b)++","++packed(L.take 16 b)++","++packed(L.drop(max 0(L.length b-16)) b)++"]"
parse :: Get a -> (a -> (Maybe String,Maybe L.ByteString)) -> L.ByteString -> Result
parse parser describe input=case runGetOrFail parser input of
 Left(rest,n,e)->Result [("error",show e),("offset",show n),("rest",suffix rest)] Nothing Nothing
 Right(rest,n,a)->let (s,b)=describe a in Result [("offset",show n),("rest",suffix rest)] s b
value a=(Just(show a),Just(encode a))
unhex []=[]
unhex(a:b:rest)=fst(head(readHex[a,b])):unhex rest
unhex _=error "odd hex"
part (name,n,header,bytes)=case name of
 "app"->JpgAppFrame(fromIntegral n)raw
 "extension"->JpgExtension(fromIntegral n)raw
 "adobe"->JpgAdobeAPP14(JpgAdobeApp14(f 0)(f 1)(f 2)([AdobeUnknown,AdobeYCbCr,AdobeYCck]!!(header!!3)))
 "jfif"->JpgJFIF(JpgJFIFApp0([JFifUnitUnknown,JFifPixelsPerInch,JFifPixelsPerCentimeter]!!(header!!0))(f 1)(f 2)(Just n))
 "exif"->let (_,rows)=runGet (getP raw :: Get(TiffHeader,[[ImageFileDirectory]])) (L.fromStrict raw) in JpgExif(head rows)
 "quant"->let TableList qs=runGet get blob in JpgQuantTable qs
 "huff"->let TableList hs=runGet get blob in JpgHuffmanTable [(h,VS.fromList(map fromIntegral header))|h<-hs]
 "scans"->JpgScans(runGet get(L.singleton(fromIntegral n)))(runGet get hdr)
 "blob"->JpgScanBlob(runGet get hdr)blob
 "restart"->JpgIntervalRestart(fromIntegral n)
 _->error "bad frame request"
 where raw=B.pack(map fromIntegral bytes);blob=L.fromStrict raw;hdr=L.pack(map fromIntegral header);f i=fromIntegral(header!!i)
run :: String -> [Int64] -> L.ByteString -> [Part] -> Result
run op ns input parts=case op of
 "strict"->p getJpgImage value
 "semi"->p (get::Get JpgImage) value
 "strictEncode"->p getJpgImage (\v->(Nothing,Just(encode v)))
 "semiEncode"->p (get::Get JpgImage) (\v->(Nothing,Just(encode v)))
 "frames"->p (skipUntilFrames>>parseFrames) (\fs->(Just(show fs),Just(encode(JpgImage fs))))
 "first"->p (skipUntilFrames>>parseToFirstFrameHeader) (\v->(Just(show v),Nothing))
 "kinds"->p (skipUntilFrames>>parseFrameKinds) (\v->(Just(show v),Nothing))
 "frame"->p (get >>= parseFrameOfKind) (\v->(Just(show v),Just(encode(JpgImage(maybe [] pure v)))))
 "take"->p (get::Get JpgImage) (\v->let fs=take(fromIntegral(head ns))(jpgFrame v) in (Just(show fs),Just(encode(JpgImage fs))))
 "firstInImage"->p (get::Get JpgImage) (\v->(Just(show(case find isHeader(jpgFrame v) of Just(JpgScans _ hdr)->Just hdr;_->Nothing)),Nothing))
 "write"->let v=JpgImage(map part parts) in Result [] (Just(show v)) (Just(encode v))
 "writeOnly"->let v=JpgImage(map part parts) in Result [] Nothing (Just(encode v))
 "printMutable"->Result [] (Just(runST(VS.thaw(VS.fromList ns)>>=printMacroBlock))) Nothing
 "printPure"->Result [] (Just(printPureMacroBlock(VS.fromList ns))) Nothing
 _->error("unknown operation "++op)
 where p parser describe=parse parser describe input
       isHeader JpgScans{}=True;isHeader _=False
render (Result fields s b)=obj(fields++maybe [] (\v->[("show",show v)]) s++maybe [] (\v->[("encoded",packed v)]) b)
external prefix (Result fields s b)=do
 case s of Nothing->pure();Just v->writeFile(prefix++".show") v
 case b of Nothing->pure();Just v->L.writeFile(prefix++".bin") v
 pure $ obj(fields++[("hasShow",if maybe False (const True) s then "true" else "false"),("hasEncoded",if maybe False (const True) b then "true" else "false")])
respond action=do
 result<-try(action >>= evaluate . force) :: IO(Either SomeException String)
 putStrLn $ either (\e->obj[("exception",show(takeWhile (/='\n')(displayException e)))]) id result
main=do
 args<-getArgs
 case args of
  ["--reference-info"]->print[compilerName,showVersion compilerVersion,"JuicyPixels","3.3.9","binary",VERSION_binary]
  ["--file",op,file,prefix]->respond $ do
    input<-B.readFile file
    external prefix(run op [1] (L.fromStrict input) [])
  _->getContents >>= mapM_ (\line->respond $ let (op,ns,hex,parts)=read line::Request in pure(render(run op ns(L.pack(unhex hex))parts))) . lines
