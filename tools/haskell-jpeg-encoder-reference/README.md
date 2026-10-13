# Original JPEG encoding oracle

Compiles the same 16 unchanged JuicyPixels 3.3.9 modules as the complete decoder
oracle. The wrapper calls fastDctLibJpeg and the exported
encodeDirectJpegAtQualityWithMetadata; it implements no JPEG encoding algorithms.
Build with GHC 9.12.2 and the frozen Cabal dependencies.

Each stdin line is a Haskell tuple (operation, color, dimensions, samples, dpi,
exif), where dimensions are [width,height,quality]. Operation dct returns all
64 Int32 coefficients and the original Int16 input after the call. Otherwise,
color is Y8, YCbCr8, RGB8 or CMYK8, and all encoded bytes are returned. Dpi is
empty, DpiX only, or [DpiX,DpiY]; nonempty exif is an ExifString at unknown tag
65000. These metadata cases check pre-encoded frame integration in Kotlin, not
the still-pending complete typed Metadatas API.

--file QUALITY INPUT OUTPUT decodes an original JPEG with decodeJpeg and writes
its complete direct JPEG re-encoding. The importer records SHA256 and length of
all output bytes at qualities 50, 90 and 100. Quality 90 covers the CMYK8 branch;
quality 100 follows validationJpegEncode/liberalJpegEncode in
the retained upstream test runner. Cross-format branches and color conversion
branches of imgToImg remain separate outstanding work. --reference-info reports
the actual compiler, JuicyPixels and vector versions.

referenceDct (the separate Float diagnostic implementation) is not exercised
by this harness and remains pending. Native unchecked invalid image-buffer
access is not a portable reference result; all nonempty images have a complete
sample vector.
