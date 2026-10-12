# Additional upstream dependency sources

`mime-types-0.1.2.2/Network/Mime.hs` comes from the Hackage source release:
https://hackage.haskell.org/package/mime-types-0.1.2.2/mime-types-0.1.2.2.tar.gz

It supplies the complete default MIME table accepted by Pandoc 3.12.1's
`mime-types >= 0.1.1 && < 0.2` constraint. The 0.1.2.2 table matches the
independent Pandoc 3.12.1 oracle, including `js`/`mjs` as `text/javascript`.
The source is MIT licensed; see `licenses/mime-types-MIT.txt`.

`tools/import-resource-tables.py` imports this table in ascending extension
order, then the overrides from the pinned Pandoc `Text.Pandoc.MIME` source.
It also imports the known URI schemes from `Text.Pandoc.URI`.
Use `--check` to verify generated Kotlin runtime resources without a network
connection or an installed Pandoc executable. The original Haskell source is
retained for attribution and reproducibility; it is not used at runtime.
