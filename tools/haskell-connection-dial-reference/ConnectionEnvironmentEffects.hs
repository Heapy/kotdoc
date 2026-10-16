{-# LANGUAGE OverloadedStrings #-}
module ConnectionEnvironmentEffects (getEnv) where
import DialEffects
import Data.IORef
import Data.Aeson
getEnv name = do
 emit $ object ["event" .= ("getEnv"::String),"name" .= map fromEnum name]
 v <- readIORef config
 check v "environmentFailure"
 case optional v "environment" of
  Nothing -> raise "io:environment-missing"
  Just x -> pure $ string x
