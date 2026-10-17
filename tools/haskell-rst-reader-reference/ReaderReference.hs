{-# LANGUAGE OverloadedStrings #-}
-- Original RST tests and complete reader; only observation wiring is new.
import qualified OriginalTests
import OracleTree
import Data.Aeson
import Data.Aeson.Types (parseEither)
import qualified Data.ByteString.Lazy.Char8 as B
import Text.Pandoc hiding (readRST)
import SourceRstReader
import Text.Pandoc.Error (renderError)
import Text.Pandoc.Sources
import Text.Parsec.Pos (newPos)
import Data.Default
import qualified Data.Text as T
import System.Environment (getArgs)
import qualified Data.Text.IO as TIO
import Control.Monad (forM_)
import Data.List (isPrefixOf)
walk path (Group name xs) = mapM_ (walk (path ++ [name])) xs
walk path (Leaf name input expected actual) = do
  let expectedDoc = either (error . show) id $ runPure $ readNative def (T.pack expected)
  if actual /= expectedDoc then error ("Original test failed: " ++ show (path ++ [name])) else pure ()
  B.putStrLn $ encode $ object ["name" .= T.intercalate "/" (map T.pack (path ++ [name])), "input" .= input,"expected" .= expectedDoc]
observe line = case eitherDecode line of
  Left e -> error e
  Right value -> case parseEither request value of
    Left e -> error e
    Right (name, sources,opts,tracing,observeShow) -> B.putStrLn $ encode $ case runPure (do setTrace tracing; d <- readRST opts sources; logs <- getLog; pure(d,logs)) of
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
  let sources = case chunks of
        Nothing -> toSources (input :: T.Text)
        Just xs -> Sources [(newPos file line col,txt) | (file,line,col,txt) <- xs]
  pure(name :: T.Text,sources, opts { readerExtensions = extensionsFromList exts },tracing,observeShow)
commands files = forM_ files $ \file -> do
  source <- TIO.readFile file
  let Pandoc _ blocks = either (error . show) id $ runPure $ readMarkdown def{readerExtensions=pandocExtensions} source
  forM_ (zip [1::Int ..] [code | CodeBlock _ code <- blocks]) $ \(n,code) -> do
    let ls = lines $ T.unpack code
    let command = dropWhile (==' ') $ drop 1 $ head ls
    let (inp,rest) = break (=="^D") $ tail ls
    B.putStrLn $ encode $ object ["file" .= file,"number" .= n,"command" .= command,"input" .= unlines inp,"expected" .= unlines (takeWhile (/=".") $ drop 1 rest)]
main = do
  args <- getArgs
  if not (null args) && head args == "--commands" then commands (tail args)
    else if args == ["--original-tests"] then mapM_ (walk []) OriginalTests.tests
    else if args == ["--defaults"] then B.putStrLn $ encode (def :: ReaderOptions)
    else B.getContents >>= mapM_ observe . B.lines
