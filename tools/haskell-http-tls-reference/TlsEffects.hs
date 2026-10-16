{-# LANGUAGE OverloadedStrings #-}
module TlsEffects where
import Effects
import HttpRedirectProjection
import Network.HTTP.Client.Internal hiding (defaultManagerSettings,newManager,httpNoBody)
import qualified Network.HTTP.Client.Internal as H
import Network.HTTP.Types (mkStatus,HttpVersion(..))
import qualified Data.ByteString as B
import qualified Data.ByteString.Char8 as S
import Data.Aeson
import Data.IORef
import qualified Network.Socket

getEnvironment=do
 emit $ object ["event" .= ("environment"::String)]
 v<-getConfig
 case optional v "environmentFailure" of {Just tag->raise tag;_ ->pure ()}
 pure [(string a,string b) | [a,b]<-map array $ maybe [] array (optional v "environment")]
proxyPolicy tag=ProxyOverride $ \secure->do
 emit $ object ["event" .= ("proxy-init"::String),"tag" .= S.unpack tag,"secure" .= secure]
 pure $ \r->r {proxy=Just $ Proxy tag 8000}
rawFactory :: String -> IO (Maybe Network.Socket.HostAddress -> String -> Int -> IO Connection)
rawFactory tag=do
 emit $ object ["event" .= ("raw-init"::String),"tag" .= tag]
 pure $ \_ host port->do
   emit $ object ["event" .= ("raw-connect"::String),"tag" .= tag,"host" .= host,"port" .= port]
   makeConnection (pure B.empty) (const $ pure ()) (pure ())
defaultManagerSettings=H.defaultManagerSettings
 {managerRawConnection=rawFactory "default",managerProxyInsecure=proxyPolicy "default-http",managerProxySecure=proxyPolicy "default-https"}
newManager settings=do
 emit $ object ["event" .= ("new-manager"::String)]
 H.newManager settings
httpNoBody r _=do
 emit $ object ["event" .= ("probe"::String),"request" .= viewReq r]
 v<-getConfig
 case optional v "probeFailure" of {Just tag->raise tag;_ ->pure ()}
 let s=maybe (object []) id $ optional v "response"
 pure $ Response {responseStatus=mkStatus (maybe 401 id $ optional s "status") (maybe "custom" bs $ optional s "message")
   ,responseVersion=HttpVersion 1 1,responseHeaders=maybe [] headers $ optional s "headers",responseBody=()
   ,responseCookieJar=maybe mempty jar $ optional s "jar",responseClose'=ResponseClose (pure ())
   ,responseOriginalRequest=r {requestBody=RequestBodyBS B.empty},responseEarlyHints=[]}
