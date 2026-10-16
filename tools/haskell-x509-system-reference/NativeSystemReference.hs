{-# LANGUAGE OverloadedStrings,PackageImports #-}
module Main where
import Data.Aeson
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import Data.X509
import Data.X509.CertificateStore
import System.X509
import Crypto.Hash
main=do
 s<-getSystemCertificateStore
 let cs=listCertificates s
 L.putStrLn $ encode $ object["count" .= length cs,"sha256" .= show(hash(B.concat $ map encodeSignedObject cs)::Digest SHA256)]
