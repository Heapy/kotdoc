{-# LANGUAGE OverloadedStrings #-}
-- Original Org tests and complete reader; only observation wiring is new.
import qualified OriginalOrgTests
import OracleTree
import Data.Aeson
import Data.Aeson.Types (parseEither)
import qualified Data.ByteString.Lazy.Char8 as B
import Text.Pandoc hiding (readOrg)
import SourceOrgReader
import Text.Pandoc.Error (renderError)
import Text.Pandoc.Sources
import Text.Parsec.Pos (newPos)
import Data.Default
import qualified Data.Text as T
import System.Environment (getArgs)
import qualified Data.Text.IO as TIO
import Control.Monad (forM_)
import Data.List (isPrefixOf,isSuffixOf)
import qualified Data.ByteString as BS
import Data.Time (UTCTime(..),secondsToDiffTime)
import Data.Time.Calendar(Day(..))
walk path (Group name xs) = mapM_ (walk (path ++ [name])) xs
walk path (Leaf name request expected actual) = do
  let expectedDoc = either (error . show) id $ runPure $ readNative def (T.pack expected)
  if actual /= expectedDoc then error ("Original test failed: " ++ show (path ++ [name])) else pure ()
  B.putStrLn $ encode $ object ["name" .= T.intercalate "/" (map T.pack (path ++ [name])), "request" .= request,"expected" .= expectedDoc]
observe line = case eitherDecode line of
  Left e -> error e
  Right value -> case parseEither request value of
    Left e -> error e
    Right (name, sources,opts,tracing,observeShow,fileDefs) -> B.putStrLn $ encode $ case runPure (do setTrace tracing; modifyPureState (\st -> st {stFiles = foldr (\(path,bytes) tree -> insertInFileTree path (FileInfo (UTCTime (ModifiedJulianDay 125) (secondsToDiffTime 0)) (BS.pack bytes)) tree) mempty fileDefs}); d <- readOrg opts sources; logs <- getLog; pure(d,logs)) of
      Left e -> object["name" .= name,"error" .= renderError e,"showError" .= show e]
      Right(d,logs) -> object $ ["name" .= name,"logs" .= logs] ++ if observeShow then ["showDocument" .= show d] else ["document" .= d]
request = withObject "request" $ \o -> do
  name <- o .: "name"
  input <- o .:? "input" .!= ""
  chunks <- o .:? "chunks"
  exts <- o .:? "extensions" .!= []
  opts <- o .:? "options" .!= def
  tracing <- o .:? "trace" .!= False
  observeShow <- o .:? "observeShow" .!= False
  fileValues <- o .:? "files" .!= []
  fileDefs <- mapM (withObject "file" (\f -> (,) <$> f .: "path" <*> f .: "bytes")) fileValues
  let sources = case chunks of
        Nothing -> toSources (input :: T.Text)
        Just xs -> Sources [(newPos file line col,txt) | (file,line,col,txt) <- xs]
  pure(name :: T.Text,sources, opts { readerExtensions = extensionsFromList exts },tracing,observeShow,fileDefs)
commands files = forM_ files $ \file -> do
  source <- TIO.readFile file
  let Pandoc _ blocks = either (error . show) id $ runPure $ readMarkdown def{readerExtensions=pandocExtensions} source
  forM_ (zip [1::Int ..] [code | CodeBlock _ code <- blocks]) $ \(n,code) -> do
    let ls = lines $ T.unpack code
    let (continuations,r1) = span ("\\" `isSuffixOf`) ls
    let command = dropWhile (==' ') $ drop 1 $ unwords (map init continuations ++ take 1 r1)
    let (inp,rest) = break (=="^D") $ drop 1 r1
    B.putStrLn $ encode $ object ["file" .= file,"number" .= n,"command" .= command,"input" .= unlines inp,"expected" .= unlines (takeWhile (/=".") $ drop 1 rest)]
main = do
  args <- getArgs
  if not (null args) && head args == "--commands" then commands (tail args)
    else if args == ["--original-tests"] then mapM_ (walk []) OriginalOrgTests.tests
    else if args == ["--defaults"] then B.putStrLn $ encode (def :: ReaderOptions)
    else B.getContents >>= mapM_ observe . B.lines
