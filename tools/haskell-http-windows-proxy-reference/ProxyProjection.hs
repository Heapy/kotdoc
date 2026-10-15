{-# LANGUAGE OverloadedStrings #-}
module ProxyProjection where
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
import System.IO.Error (ioeGetErrorString)

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
 | Just io <- fromException e :: Maybe IOError = object ["kind" .= ("io"::String),"message" .= ioeGetErrorString io]
 | Just ThreadKilled <- fromException e = object ["kind" .= ("async"::String)]
 | otherwise = error $ "unclassified: " ++ show e
observe :: IO Value -> IO Value
observe action = do
 result <- try $ do {v<-action;_<-evaluate $ L.length $ encode v;pure v}
 pure $ either (\e -> object ["error" .= failure e]) id result
