{-# LANGUAGE CPP, OverloadedStrings, ScopedTypeVariables, PatternSynonyms #-}
#include <stddef.h>
#include <stdint.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <sys/un.h>
#include <netdb.h>
module Main where
import qualified Network.Socket as N
import qualified Network.Socket.Types as NT
import qualified Network.Socket.Info as NI
import SocketPatterns
import GhcErrnoPatterns
import Foreign.C.Error (Errno(..),errnoToIOError)
import qualified GHC.Internal.IO.Exception as GE
import Foreign
import Foreign.C.Types
import qualified GHC.Internal.Foreign.C.String.Encoding as Enc
import GHC.Internal.IO.Encoding (mkTextEncoding,setForeignEncoding)
import Data.Char
import Data.Word
import Data.Int
import Control.Exception
import Control.Monad
import qualified Data.Aeson as J
import Data.Aeson (Value(..),(.=),object,toJSON)
import qualified Data.Aeson.KeyMap as K
import Data.Aeson.Types (parseMaybe)
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Maybe
import qualified Data.Foldable as F
import System.IO
import System.IO.Error
import System.Info (fullCompilerVersion)
import Data.Version (showVersion)

field :: J.FromJSON a => Value -> J.Key -> a
field (Object o) k=fromJust $ K.lookup k o >>= parseMaybe J.parseJSON
field _ _=error "object"
optional :: J.FromJSON a => Value -> J.Key -> Maybe a
optional (Object o) k=case K.lookup k o of {Just Null->Nothing;Just x->parseMaybe J.parseJSON x;Nothing->Nothing}
optional _ _=Nothing
value (Object o) k=fromJust $ K.lookup k o
value _ _=error "object"
array :: Value -> [Value]
array=fromJust . parseMaybe J.parseJSON
string v=map chr (fromJust $ parseMaybe J.parseJSON v :: [Int])
points=map ord
addr v=case field v "kind" :: String of
 "v4"->N.SockAddrInet (fromIntegral (field v "port" :: Integer)) (field v "host")
 "v6"->N.SockAddrInet6 (fromIntegral (field v "port" :: Integer)) (field v "flow") (field v "host") (field v "scope")
 "unix"->N.SockAddrUnix (string $ value v "path")
 _->error "address"
viewAddr a=object $ ["show" .= points (show a),"prec11" .= points (showsPrec 11 a "TAIL")]++case a of
 N.SockAddrInet p h->["kind" .= ("v4"::String),"port" .= (fromIntegral p::Int),"host" .= h]
 N.SockAddrInet6 p f h s->["kind" .= ("v6"::String),"port" .= (fromIntegral p::Int),"flow" .= f,"host" .= h,"scope" .= s]
 N.SockAddrUnix p->["kind" .= ("unix"::String),"path" .= points p]
flag s=read s :: N.AddrInfoFlag
info v=N.AddrInfo (map flag $ fromMaybe [] $ optional v "flags") (N.unpackFamily $ fromIntegral (fromMaybe 0 $ optional v "family" :: Int))
 (N.GeneralSocketType $ fromIntegral (fromMaybe 0 $ optional v "type" :: Int)) (fromIntegral (fromMaybe 0 $ optional v "protocol" :: Int))
 (maybe (N.SockAddrInet 0 0) addr $ optional v "address") (string <$> optional v "canon")
viewInfo x=object ["show" .= points (show x),"prec11" .= points (showsPrec 11 x "TAIL")
 ,"flags" .= map show (N.addrFlags x),"family" .= (fromIntegral (N.packFamily $ N.addrFamily x)::Int)
 ,"type" .= (case N.addrSocketType x of N.GeneralSocketType n->fromIntegral n::Int)
 ,"protocol" .= (fromIntegral (N.addrProtocol x)::Int),"address" .= viewAddr (N.addrAddress x),"canon" .= fmap points (N.addrCanonName x)]
failure e
 | Just (io::IOException)<-fromException e=object ["kind" .= ("io"::String),"show" .= points (show io),"type" .= show (ioeGetErrorType io),"location" .= points (ioeGetLocation io),"errno" .= fmap (fromIntegral :: CInt -> Int) (GE.ioe_errno io),"filename" .= fmap points (ioeGetFileName io),"description" .= points (GE.ioe_description io)]
 | Just (ErrorCall s)<-fromException e=object ["kind" .= ("fatal"::String),"show" .= points s]
 | otherwise=error $ show e
observe action=do
 r<-try $ do {x<-action;_<-evaluate $ L.length $ J.encode x;pure x}
 pure $ either (\e->object ["error" .= failure e]) id r
withNodes [] action=action nullPtr
withNodes (v:vs) action=withNodes vs $ \next->NT.withSockAddr (maybe (N.SockAddrInet 0 0) addr (optional v "address")) $ \sa _->do
 let withCanon f=case optional v "canonBytes" of
       Nothing->f nullPtr
       Just raw->withArray0 0 (map fromIntegral (raw::[Int])::[Word8]) (f . castPtr)
 withCanon $ \canon->alloca $ \node->do
  poke node (info v)
  (#{poke struct addrinfo, ai_addr}) node sa
  (#{poke struct addrinfo, ai_canonname}) node canon
  (#{poke struct addrinfo, ai_next}) node next
  action node
abi=object
 ["pointerSize" .= sizeOf (nullPtr::Ptr ()),"littleEndian" .= ((#const BYTE_ORDER)==(#const LITTLE_ENDIAN))
 ,"errnoTypes" .= [(fromIntegral n::Int,t) | (Errno n,t)<-errnoTypePatterns]
 ,"families" .= [(s,fromIntegral (N.packFamily f)::Int) | (f,s)<-familyBijection]
 ,"socketTypes" .= [(s,case t of N.GeneralSocketType n->fromIntegral n::Int) | (t,s)<-socktypeBijection]
 ,"aiFlags" .= [(show f,fromIntegral n::Int) | (f,n)<-NI.aiFlagMapping]
 ,"niFlags" .= [(show f,fromIntegral n::Int) | (f,n)<-NI.niFlagMapping]
 ,"addrInfo" .= object ["size" .= (#{size struct addrinfo} :: Int),"alignment" .= alignment (undefined::N.AddrInfo)
   ,"flags" .= (#{offset struct addrinfo, ai_flags} :: Int),"family" .= (#{offset struct addrinfo, ai_family} :: Int)
   ,"type" .= (#{offset struct addrinfo, ai_socktype} :: Int),"protocol" .= (#{offset struct addrinfo, ai_protocol} :: Int)
   ,"addrlen" .= (#{offset struct addrinfo, ai_addrlen} :: Int),"addr" .= (#{offset struct addrinfo, ai_addr} :: Int)
   ,"canon" .= (#{offset struct addrinfo, ai_canonname} :: Int),"next" .= (#{offset struct addrinfo, ai_next} :: Int),"pokeAddrlenSize" .= sizeOf (undefined::CSize)]
 ,"sockAddr" .= object ["familyOffset" .= (#{offset struct sockaddr, sa_family} :: Int),"familySize" .= (#{const sizeof(((struct sockaddr*)0)->sa_family)} :: Int)
#ifdef darwin_HOST_OS
   ,"lengthOffset" .= (#{offset struct sockaddr, sa_len} :: Int)
#else
   ,"lengthOffset" .= (-1::Int)
#endif
   ,"v4Size" .= (#{size struct sockaddr_in} :: Int),"v4Port" .= (#{offset struct sockaddr_in, sin_port} :: Int),"v4Host" .= (#{offset struct sockaddr_in, sin_addr} :: Int)
   ,"v6Size" .= (#{size struct sockaddr_in6} :: Int),"v6Port" .= (#{offset struct sockaddr_in6, sin6_port} :: Int),"v6Flow" .= (#{offset struct sockaddr_in6, sin6_flowinfo} :: Int)
   ,"v6Host" .= (#{offset struct sockaddr_in6, sin6_addr} :: Int),"v6Scope" .= (#{offset struct sockaddr_in6, sin6_scope_id} :: Int)
   ,"unixSize" .= (#{size struct sockaddr_un} :: Int),"unixPath" .= (#{offset struct sockaddr_un, sun_path} :: Int),"unixMax" .= (#{const sizeof(((struct sockaddr_un*)0)->sun_path)} :: Int)]
#ifdef linux_HOST_OS
 ,"linuxAbstractUnix" .= True
#else
 ,"linuxAbstractUnix" .= False
#endif
 ]
run v=observe $ case field v "api" :: String of
 "abi"->pure abi
 "codec"->do
   let name=field v "name" :: String
   enc<-mkTextEncoding (name++"//IGNORE")
   let mode=field v "mode" :: String
   if mode=="encode" then do
     let str=string $ value v "points";nul=fromMaybe True $ optional v "nullTerminate"
     (if nul then Enc.withCStringLen0 else Enc.withCStringLen) enc str $ \(p,n)->toJSON . (map fromIntegral :: [Word8]->[Int]) <$> peekArray n (castPtr p)
    else withArray0 0 (map fromIntegral (field v "bytes" :: [Int])::[Word8]) $ \p->toJSON . points <$> (if mode=="decode" then Enc.peekCStringLen enc (castPtr p,length (field v "bytes" :: [Int])) else Enc.peekCString enc (castPtr p))
 "codec-pairs"->do
   enc<-mkTextEncoding ((field v "name" :: String)++"//IGNORE")
   let start=field v "start" :: Int;count=field v "count" :: Int
   xs<-forM [start..start+count-1] $ \n->withArray ([fromIntegral (n `div` 256),fromIntegral (n `mod` 256)]::[Word8]) $ \p->points <$> Enc.peekCStringLen enc (castPtr p,2)
   pure $ toJSON xs
 "follow"->do
   enc<-mkTextEncoding "UTF-8//IGNORE";setForeignEncoding enc
   withNodes (array $ value v "nodes") $ \node->toJSON . map viewInfo . F.toList <$> NI.followAddrInfo node
 "errno"->throwIO $ errnoToIOError (fromMaybe "test-location" (optional v "location")) (Errno $ fromIntegral (field v "errno" :: Int64)) Nothing (string <$> optional v "filename")
 "address"->let a=addr $ value v "address" in NT.withSockAddr a $ \p n->do
   raw<-peekArray n (castPtr p::Ptr Word8)
   back<-NT.peekSockAddr p
   pure $ object ["size" .= n,"bytes" .= raw,"value" .= viewAddr a,"peek" .= viewAddr back]
 "peek"->allocaBytes 256 $ \p->do
   fillBytes p 0 256
   pokeArray (castPtr p) (map fromIntegral (field v "bytes" :: [Int])::[Word8])
   viewAddr <$> NT.peekSockAddr p
 "compare"->let a=addr $ value v "left";b=addr $ value v "right" in pure $ object ["eq" .= (a==b),"order" .= show (compare a b)]
 "integer-types"->do
   let n=fromIntegral (field v "value" :: Int64);f=N.unpackFamily n;t=N.GeneralSocketType n;p=fromMaybe 0 $ optional v "precedence"
   pure $ object ["family" .= showsPrec p f "TAIL","type" .= showsPrec p t "TAIL","familySupported" .= N.isSupportedFamily f,"typeSupported" .= N.isSupportedSocketType t]
 "hints"->allocaBytes (sizeOf (undefined::N.AddrInfo)) $ \p->do
   fillBytes p 170 (sizeOf (undefined::N.AddrInfo));poke (castPtr p) (info $ value v "hints")
   raw<-peekArray (sizeOf (undefined::N.AddrInfo)) (castPtr p::Ptr Word8)
   pure $ object ["value" .= viewInfo (info $ value v "hints"),"bytes" .= raw]
 "flags"->pure $ object ["pack" .= (fromIntegral (NI.packBits NI.aiFlagMapping (map flag $ field v "flags"))::Int),"unpack" .= map show (NI.unpackBits NI.aiFlagMapping (fromIntegral (field v "mask" :: Int64)::CInt))]
 "resolve"->do
   enc<-mkTextEncoding (fromMaybe "UTF-8" (optional v "encoding")++"//IGNORE");setForeignEncoding enc
   xs<-NI.getAddrInfoList (info <$> optional v "hints") (string <$> optional v "host") (string <$> optional v "service")
   pure $ if optional v "void"==Just True then Bool True else toJSON $ map viewInfo xs
 _->error "api"
main=do
 unless (showVersion fullCompilerVersion=="9.12.2") $ error "GHC 9.12.2 required"
 hSetBuffering stdout LineBuffering
 L.getContents >>= mapM_ (\line->case J.eitherDecode line of {Left e->error e;Right v->run v >>= L.putStrLn . J.encode}) . L.lines
