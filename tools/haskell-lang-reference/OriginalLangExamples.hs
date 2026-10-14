{-# LANGUAGE OverloadedStrings #-}
-- Verbatim original langPairs/emptyLang; BSD-2-Clause notice in licenses/.
module OriginalLangExamples where
import Data.Text (Text)
import Text.Collate.Lang

emptyLang :: Lang
emptyLang = Lang mempty mempty mempty mempty mempty mempty

langPairs :: [(Text, Lang)]
langPairs = [ ("en", emptyLang{langLanguage = "en"})
            , ("en with garbage following",
                     emptyLang{langLanguage = "en"})
            , ("en--with garbage following", -- CSL allows this
                     emptyLang{langLanguage = "en"})
            , ("en-US", emptyLang{langLanguage = "en", langRegion = Just "US"})
            , ("sr_Latn_RS", emptyLang{langLanguage = "sr",
                                   langScript = Just "Latn",
                                   langRegion = Just "RS"})
            , ("es-419", emptyLang{langLanguage = "es",
                                   langRegion = Just "419"})
            , ("de-CH-1996", emptyLang{langLanguage = "de",
                                       langRegion = Just "CH",
                                       langVariants = ["1996"]})
            , ("en-u-kr-latin-digit", emptyLang{langLanguage = "en",
                     langExtensions = [("u", [("kr", "latin-digit")])]})
            ]

