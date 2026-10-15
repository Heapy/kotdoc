{-# LANGUAGE OverloadedStrings #-}
module Text.Pandoc.Options (defaultMathJaxURL, defaultKaTeXURL) where
import Data.Text (Text)
defaultMathJaxURL :: Text
defaultMathJaxURL = "https://cdn.jsdelivr.net/npm/mathjax@4/tex-chtml.js"

defaultKaTeXURL :: Text
defaultKaTeXURL = "https://cdn.jsdelivr.net/npm/katex@latest/dist/"
