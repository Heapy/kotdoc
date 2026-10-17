{-# LANGUAGE OverloadedStrings #-}
import Data.Aeson
import Data.Aeson.Types(parseEither,Parser)
import qualified Data.ByteString as BS
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.ByteString.Lazy as BL
import qualified Data.Text as T
import qualified Data.Map as M
import Text.Pandoc hiding(readRST)
import FullSourceRstReader
import Text.Pandoc.Error(renderError)
import Text.Pandoc.Class
import Text.Pandoc.Sources
import Text.Pandoc.Parsing(widthsFromIndices)
import Text.Pandoc.Shared(tabFilter,blocksToInlines)
import qualified Data.Text.Encoding as TE
import Text.Parsec.Pos(newPos)
import Data.Default
import Data.Time.Clock(UTCTime(..),picosecondsToDiffTime)
import Data.Time.Calendar(Day(..))
import Data.Time.Format(formatTime,defaultTimeLocale)
import Text.Read(readMaybe)
import Data.Word(Word8)
request :: Value -> Parser (T.Text,T.Text,Sources,ReaderOptions,Bool,[(FilePath,[Word8])],UTCTime,[Int],[Int],[(T.Text,T.Text)],Maybe T.Text,[FilePath],[(FilePath,[Word8])])
request=withObject "request" $ \o->do
 name<-o .: "name";kind<-o .:? "kind" .!= "reader";input<-o .:? "input" .!= "";chunks<-o .:? "chunks";exts<-o .:? "extensions" .!= [];opts<-o .:? "options" .!= def;tracing<-o .:? "trace" .!= False
 files<-o .:? "files" .!= [];stamp<-o .:? "timestamp" .!= "1970-01-01 00:00:00 UTC";utc<-o .:? "utc";indices<-o .:? "indices" .!= [];spans<-o .:? "spans" .!= [];environment<-o .:? "environment" .!= [];sourceURL<-o .:? "sourceURL";resourcePath<-o .:? "resourcePath" .!= ["."];media<-o .:? "media" .!= []
 let sources=case chunks of Nothing->toSources(input::T.Text);Just xs->Sources[(newPos file line col,txt)|(file,line,col,txt)<-xs]
 pure(name,kind,sources,opts{readerExtensions=extensionsFromList exts},tracing,files,maybe (maybe(error "bad timestamp")id(readMaybe stamp)) (\(d,p)->UTCTime(ModifiedJulianDay(read d))(picosecondsToDiffTime(read p))) utc,indices,spans,environment,sourceURL,resourcePath,media)
main=B.getContents >>= mapM_ observe.B.lines
observe line=case eitherDecode line >>=parseEither request of
 Left e->error e
 Right(name,kind,sources,opts,tracing,files,stamp,indices,spans,environment,sourceURL,resourcePath,media)->B.putStrLn$encode$
  if kind=="flatten" then case eitherDecodeStrict(TE.encodeUtf8$sourcesToText sources)of Left e->error e;Right(Pandoc _ bs)->object["name" .=name,"result" .=blocksToInlines bs]
  else if kind=="flatten-deep" then let depth=read(T.unpack$sourcesToText sources)::Int;body=iterate (\b->Div("",[],[])[b]) (Para[Str "x"])!!depth in object["name" .=name,"result" .=blocksToInlines [body]]
  else if kind=="app-tabs" then object["name" .=name,"result" .=tabFilter(readerTabStop opts)(sourcesToText sources)]
  else if kind=="col-spans" then object["name" .=name,"result" .=colSpans indices spans]
  else if kind=="widths" then object["name" .=name,"result" .=widthsFromIndices(readerColumns opts)indices]
  else if kind=="unicode" then object["name" .=name,"result" .=unicodeTransform(sourcesToText sources)]
  else if kind=="chunks" then object["name" .=name,"result" .=toChunks(sourcesToText sources)]
  else if kind=="date" then object["name" .=name,"result" .=formatTime defaultTimeLocale(T.unpack$sourcesToText sources)stamp]
  else case runPure(do
        modifyPureState $ \s -> s{stTime=stamp,stEnv=environment++stEnv s,stFiles=foldr(\(p,bs)->insertInFileTree p(FileInfo stamp(BS.pack bs)))mempty files}
        setInputFiles(maybe [] (\s->[T.unpack s])sourceURL)
        setResourcePath resourcePath
        mapM_ (\(p,bs)->insertMedia p Nothing(BL.pack bs)) media
        setTrace tracing;d<-if kind=="fetch-reader" then do { _<-fetchItem(sourcesToText sources); pure(Pandoc mempty []) } else readRST opts sources;logs<-getLog;pure(d,logs))of
        Left e->object["name" .=name,"error" .=renderError e,"showError" .=show e]
        Right(d,logs)->object $ ["name" .=name,"logs" .=logs] ++ if kind=="reader-show" then ["showDocument" .=show d] else ["document" .=d]
