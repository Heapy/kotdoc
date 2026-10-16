{-# LANGUAGE OverloadedStrings #-}
module Effects where
import HttpRedirectProjection
import Control.Exception
import Data.Aeson
import qualified Data.Default
import qualified Data.ByteString.Lazy as L
import Data.IORef
import System.IO.Unsafe
import Data.Char (chr,ord)
import qualified Data.ByteString as B
import qualified Data.Text as T
import qualified Network.Connection as NC
import qualified Network.TLS as TLS
import Network.HTTP.Client.Internal
import System.IO.Error

config :: IORef Value
{-# NOINLINE config #-}
config=unsafePerformIO $ newIORef $ object []
logs :: IORef [Value]
{-# NOINLINE logs #-}
logs=unsafePerformIO $ newIORef []
emit :: Value -> IO ()
emit v=do
 _<-evaluate $ L.length $ encode v
 modifyIORef' logs (v:)
getConfig=readIORef config
string :: Value -> String
string (String t)=T.unpack t
string v=map chr (field (object ["v" .= v]) "v")
points=map ord
sockView Nothing=Null
sockView (Just s)=case s of
 NC.SockSettingsSimple h p->object ["kind" .= ("simple"::String),"host" .= points h,"port" .= (fromIntegral p::Int)]
 NC.SockSettingsEnvironment n->object ["kind" .= ("environment"::String),"name" .= fmap points n]
 NC.OtherProxy h p->object ["kind" .= ("other"::String),"host" .= points h,"port" .= (fromIntegral p::Int)]
optValue v key=case optional v key of {Just Null->Nothing;x->x}
sock v=case field v "kind"::String of
 "simple"->NC.SockSettingsSimple (string $ value v "host") (fromIntegral (field v "port"::Integer))
 "environment"->NC.SockSettingsEnvironment (fmap string $ optValue v "name")
 "other"->NC.OtherProxy (string $ value v "host") (fromIntegral (field v "port"::Integer))
 _->error "sock"
tls n=case n::Int of
 0->NC.TLSSettingsSimple False False False supported
 1->NC.TLSSettingsSimple True False True supported
 _->NC.TLSSettingsSimple False True True supported
 where supported=NC.settingClientSupported (Data.Default.def::NC.TLSSettings)
tlsView Nothing=Null
tlsView (Just s)=toJSON $ if NC.settingDisableCertificateValidation s then (1::Int) else if NC.settingDisableSession s then 2 else 0
raise :: String -> IO a
raise tag=case tag of
 "io"->ioError $ ioeSetErrorString (mkIOError userErrorType "script-location" Nothing Nothing) "script-io"
 "vanished"->ioError $ mkIOError resourceVanishedErrorType "script-location" Nothing Nothing
 "post-eof"->throwIO $ TLS.PostHandshake TLS.Error_EOF
 "handshake-eof"->throwIO $ TLS.HandshakeFailed TLS.Error_EOF
 "uncontextualized-eof"->throwIO $ TLS.Uncontextualized TLS.Error_EOF
 "terminated-eof"->throwIO $ TLS.Terminated True "reason" TLS.Error_EOF
 "post-misc"->throwIO $ TLS.PostHandshake (TLS.Error_Misc "misc")
 "tls-not-established"->throwIO TLS.ConnectionNotEstablished
 "tls-missing-handshake"->throwIO TLS.MissingHandshake
 "line"->throwIO NC.LineTooLong
 "resolve"->throwIO $ NC.HostNotResolved "host"
 "connect"->throwIO $ NC.HostCannotConnect "host" [mkIOError userErrorType "one" Nothing Nothing,mkIOError resourceVanishedErrorType "two" Nothing Nothing]
 "content-closed"->throwHttp ConnectionClosed
 "request-closed"->throwIO $ HttpExceptionRequest defaultRequest ConnectionClosed
 "nodata"->throwHttp NoResponseDataReceived
 "incomplete"->throwHttp IncompleteHeaders
 "async"->throwIO ThreadKilled
 _->throwIO $ ErrorCall tag
failure se
 | Just e<-fromException se::Maybe HttpException = object ["kind" .= ("request"::String),"show" .= show e]
 | Just e<-fmap unHttpExceptionContentWrapper (fromException se) = object ["kind" .= ("content"::String),"show" .= show e]
 | Just e<-fromException se::Maybe IOError = object ["kind" .= ("io"::String),"show" .= show e]
 | Just e<-fromException se::Maybe TLS.TLSException = object ["kind" .= ("tls"::String),"show" .= show e]
 | Just e<-fromException se::Maybe NC.LineTooLong = object ["kind" .= ("line"::String),"show" .= show e]
 | Just e<-fromException se::Maybe NC.HostNotResolved = object ["kind" .= ("resolve"::String),"show" .= show e]
 | Just e<-fromException se::Maybe NC.HostCannotConnect = object ["kind" .= ("connect"::String),"show" .= show e]
 | Just ThreadKilled<-fromException se=object ["kind" .= ("async"::String),"show" .= ("thread killed"::String)]
 | Just (ErrorCall s)<-fromException se=object ["kind" .= ("fatal"::String),"show" .= takeWhile (/='\n') s]
 | otherwise=error $ show se
attempt action=do
 x<-try action
 pure $ either (\e->object ["error" .= failure (e::SomeException)]) id x
