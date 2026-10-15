{-# LANGUAGE OverloadedStrings #-}
module Main where
import OriginalProxy
import ProxyEnvironment (environment)
import Network.HTTP.Client.Internal
import Control.Exception
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import Data.Aeson.Types (parseMaybe)
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.CaseInsensitive as CI
import qualified Data.Text as T
import Data.IORef
import Data.Maybe (fromJust)
import Data.Char (chr)
import System.IO

field :: FromJSON a => Value -> Key -> a
field (Object o) k = fromJust (K.lookup k o >>= parseMaybe parseJSON)
field _ _ = error "expected object"
optional :: FromJSON a => Value -> Key -> Maybe a
optional (Object o) k = K.lookup k o >>= parseMaybe parseJSON
optional _ _ = Nothing
bs :: Value -> B.ByteString
bs = B.pack . fromJust . parseMaybe parseJSON
bytes = B.unpack
readProxy :: Value -> Proxy
readProxy v = Proxy (bs $ field v "host") (field v "port")
request :: Value -> Request
request v = defaultRequest {host=bs $ field v "host",proxy=readProxy <$> optional v "proxy"
 ,requestHeaders=maybe [] (map (\[a,b] -> (CI.mk $ bs a,bs b))) (optional v "headers")
 ,secure=maybe False id (optional v "secure"),method=maybe "GET" bs (optional v "method")}
viewReq r = object ["proxy" .= fmap show (Network.HTTP.Client.Internal.proxy r)
 ,"headers" .= [[bytes $ CI.original a,bytes b] | (a,b)<-requestHeaders r]
 ,"host" .= bytes (host r),"secure" .= secure r,"method" .= bytes (method r),"show" .= show r]
failure :: SomeException -> Value
failure e
 | Just content <- fmap unHttpExceptionContentWrapper (fromException e) = object
    ["kind" .= ("content"::String),"show" .= show e,"content" .= show content,"prec11" .= showsPrec 11 content "TAIL"]
 | Just (ErrorCall msg) <- fromException e = object ["kind" .= ("error"::String),"message" .= takeWhile (/='\n') msg]
 | Just (PatternMatchFail msg) <- fromException e = object ["kind" .= ("pattern"::String),"message" .= takeWhile (/='\n') msg]
 | otherwise = error $ "unclassified: " ++ show e
observe :: IO Value -> IO Value
observe action = do
 result <- try $ do {v<-action;_<-evaluate $ L.length $ encode v;pure v}
 pure $ either (\e -> object ["error" .= failure e]) id result
run :: Value -> IO Value
run v = do
 writeIORef environment $ readEnvironment $ field v "env"
 let name=T.pack $ map chr $ field v "name"
     views=field v "hosts" :: [Value]
     protocol=httpProtocol $ maybe False id (optional v "secure")
 if (field v "api" :: String)=="env" then do
   made <- try $ envHelper name
   case made of
    Left e -> pure $ object ["constructionError" .= failure e]
    Right f -> do
      maybe (pure ()) (writeIORef environment . readEnvironment) (optional v "afterEnv")
      results<-mapM (\h -> observe $ pure $ case f (bs h) of
        Nothing -> Null
        Just settings -> object ["show" .= show settings,"prec11" .= showsPrec 11 settings "TAIL"]) views
      pure $ object ["results" .= results]
 else do
   let fallback=case field v "fallback" :: String of
        "request" -> EHFromRequest
        "none" -> EHNoProxy
        "use" -> EHUseProxy (readProxy $ field v "fallbackProxy")
        _ -> error "fallback"
   made <- try $ systemProxyHelper (T.pack . map chr <$> optional v "override") protocol fallback
   case made of
    Left e -> pure $ object ["constructionError" .= failure e]
    Right f -> do
      maybe (pure ()) (writeIORef environment . readEnvironment) (optional v "afterEnv")
      results<-mapM (\h -> observe $ pure $ viewReq $ f (request h)) views
      pure $ object ["results" .= results]
readEnvironment :: [[[Int]]] -> [(String,String)]
readEnvironment = map (\[a,b] -> (map chr a,map chr b))
main :: IO ()
main = do
 hSetBuffering stdout LineBuffering
 L.getContents >>= mapM_ (\line -> case eitherDecode line of
   Left e -> error e
   Right v -> run v >>= L.putStrLn . encode) . L.lines
