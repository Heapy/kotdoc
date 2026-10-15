{-# LANGUAGE PackageImports #-}
module System.Directory (module D,listDirectory,doesDirectoryExist,getXdgDirectory,getAppUserDataDirectory) where
import "directory" System.Directory as D hiding(listDirectory,doesDirectoryExist,getXdgDirectory,getAppUserDataDirectory)
import Hooks
import Data.Aeson
import qualified Data.Vector as V
listDirectory p=do Array v<-call "list" [p];pure(map chars(V.toList v))
doesDirectoryExist p=as <$> call "directory" [p]
getXdgDirectory kind app=chars <$> call "xdg" [show kind,app]
getAppUserDataDirectory app=chars <$> call "legacy" [app]
