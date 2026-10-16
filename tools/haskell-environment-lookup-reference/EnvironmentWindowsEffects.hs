{-# LANGUAGE OverloadedStrings #-}
module EnvironmentWindowsEffects (module OriginalWindowsErrno,getLastError,c_getErrorMessage,localFree,peekCWString) where
import OriginalWindowsErrno
import LookupEffects
import Data.Aeson
import Data.IORef
import Foreign
import qualified OriginalWindowsWide as W
getLastError=do
 code<-atomicModifyIORef' errorCodes $ \xs->case xs of []->([],203);[x]->([x],x);x:rest->(rest,x)
 emit $ object ["lastError" .= code]
 pure code
c_getErrorMessage code=do
 emit $ object ["format" .= code]
 check "formatFailure"
 readIORef messagePtr
localFree _=emit (String "free") >> pure nullPtr
peekCWString p=W.cWcharsToChars <$> peekArray0 0 p
