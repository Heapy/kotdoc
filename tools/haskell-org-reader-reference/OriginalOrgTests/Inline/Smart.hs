{-# LANGUAGE OverloadedStrings #-}
{- |
   Module      : OriginalOrgTests.Inline.Smart
   Copyright   : © 2014-2024 Albert Krewinkel
   License     : GNU GPL, version 2 or above

   Maintainer  : Albert Krewinkel <albert+pandoc@tarleb.com>
   Stability   : alpha
   Portability : portable

Test smart parsing of quotes, apostrophe, etc.
-}
module OriginalOrgTests.Inline.Smart (tests) where

import Data.Text (Text)
import OracleTree (TestTree)
import OracleHelpers ((=?>), purely, test, testProfile)
import Text.Pandoc (ReaderOptions (readerExtensions),
                    Extension (Ext_smart), def, enableExtension,
                    getDefaultExtensions)
import SourceOrgReader (readOrg)
import Text.Pandoc.Builder

orgSmart :: Text -> Pandoc
orgSmart = purely $
  let extensionsSmart = enableExtension Ext_smart (getDefaultExtensions "org")
  in readOrg def{ readerExtensions = extensionsSmart }

tests :: [TestTree]
tests =
  [ testProfile "smart" orgSmart "quote before ellipses"
    ("'...hi'"
     =?> para (singleQuoted "…hi"))

  , testProfile "smart" orgSmart "apostrophe before emph"
    ("D'oh! A l'/aide/!"
     =?> para ("D’oh! A l’" <> emph "aide" <> "!"))

  , testProfile "smart" orgSmart "apostrophe in French"
    ("À l'arrivée de la guerre, le thème de l'«impossibilité du socialisme»"
     =?> para "À l’arrivée de la guerre, le thème de l’«impossibilité du socialisme»")

  , testProfile "smart" orgSmart "Quotes cannot occur at the end of emphasized text"
    ("/say \"yes\"/" =?>
     para ("/say" <> space <> doubleQuoted "yes" <> "/"))

  , testProfile "smart" orgSmart "Dashes are allowed at the borders of emphasis'"
    ("/foo---/" =?>
     para (emph "foo—"))

  , testProfile "smart" orgSmart "Support for shy (soft) hyphen"
    ("Ur\\-instinkt" =?>
     para "Ur\173instinkt")

  , testProfile "smart" orgSmart "Single quotes can be followed by emphasized text"
    ("Singles on the '/meat market/'" =?>
     para ("Singles on the " <> singleQuoted (emph "meat market")))

  , testProfile "smart" orgSmart "Double quotes can be followed by emphasized text"
    ("Double income, no kids: \"/DINK/\"" =?>
     para ("Double income, no kids: " <> doubleQuoted (emph "DINK")))
  ]
