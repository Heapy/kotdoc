{-# LANGUAGE OverloadedStrings #-}
module ProxyRegistry where
import ProxyRegistryTypes
import ProxyEffects
import ProxyProjection (field,optional)
import Data.Aeson
import Data.IORef
import Foreign
import Data.Char (chr)

hKEY_CURRENT_USER :: HKEY
hKEY_CURRENT_USER = 1
rEG_DWORD :: DWORD
rEG_DWORD = 4
regOpenKey :: HKEY -> String -> IO HKEY
regOpenKey root path = do
 event [String "open",toJSON root,toJSON path]
 raise "open"
 pure 7
regCloseKey :: HKEY -> IO ()
regCloseKey key = do
 event [String "close",toJSON key]
 raise "close"
regQueryValueEx :: HKEY -> String -> Ptr Word8 -> Int -> IO DWORD
regQueryValueEx key name ptr size = do
 event [String "dword",toJSON key,toJSON name,toJSON size]
 raise "dword"
 v<-readIORef config
 poke (castPtr ptr) (maybe 1 id (optional v "enabled") :: DWORD)
 pure $ maybe 4 id (optional v "valueType")
regQueryValue :: HKEY -> Maybe String -> IO String
regQueryValue key name = do
 event [String "string",toJSON key,toJSON name]
 let fieldName=if name==Just "ProxyServer" then "server" else "overrideValue"
 raise $ if name==Just "ProxyServer" then "server" else "override"
 v<-readIORef config
 case optional v fieldName of
  Nothing -> ioError $ userError "missing value"
  Just points -> pure $ map chr points
