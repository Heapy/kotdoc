module Paths_pandoc where
import Hooks
getDataDir=chars <$> call "dataDir" []
