{-# LANGUAGE OverloadedStrings #-}
module ConnectionSocketEffects (module N,socket,connect,close,getAddrInfo) where
import Network.Socket as N hiding (socket,connect,close,getAddrInfo)
import qualified Network.Socket as Real
import qualified DialEffects as F
import Data.IORef
import Data.Aeson
socket :: Family -> SocketType -> ProtocolNumber -> IO Socket
socket family typ protocol = do
 v <- readIORef F.config
 let entry = head [a | a <- maybe [] id $ F.optional v "addresses", (F.field a "id"::Int) == fromIntegral protocol]
 F.emit $ object ["event" .= ("socket"::String),"id" .= (fromIntegral protocol::Int),"family" .= show family,"type" .= show typ]
 F.check entry "createFailure"
 sock <- Real.socket AF_INET Stream defaultProtocol
 modifyIORef' F.sockets ((sock,entry):)
 pure sock
connect :: Socket -> SockAddr -> IO ()
connect sock address = do
 entry <- F.descriptor sock
 let port = case address of SockAddrInet p _ -> fromIntegral p::Int; _ -> -1
 F.emit $ object ["event" .= ("connect"::String),"id" .= (F.field entry "id"::Int),"addressPort" .= port]
 F.check entry "connectFailure"
close :: Socket -> IO ()
close sock = do
 entry <- F.descriptor sock
 F.emit $ object ["event" .= ("close-socket"::String),"id" .= (F.field entry "id"::Int)]
 Real.close sock
 F.check entry "closeFailure"
getAddrInfo :: Maybe AddrInfo -> Maybe HostName -> Maybe ServiceName -> IO [AddrInfo]
getAddrInfo hints host service = do
 F.emit $ object ["event" .= ("resolve"::String),"host" .= fmap (map fromEnum) host,"service" .= service,
                  "flags" .= fmap (map show . addrFlags) hints,"type" .= fmap (show . addrSocketType) hints]
 v <- readIORef F.config
 F.check v "resolveFailure"
 pure [defaultHints {addrFamily=AF_INET,addrSocketType=Stream,addrProtocol=fromIntegral (F.field a "id"::Int),
                     addrAddress=SockAddrInet (fromIntegral (F.field a "id"::Int)) 0}
      |a <- maybe [] id $ F.optional v "addresses"]
