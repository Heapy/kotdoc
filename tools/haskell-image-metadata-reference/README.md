# Original image metadata oracle

Compiles 16 unchanged JuicyPixels 3.3.9 modules with GHC 9.12.2 and the frozen
Cabal dependencies. All storage operations, folds, helper conversions, TIFF
metadata extraction/encoding, JFIF metadata and complete JPEG paths call the
original exports. The adapter only constructs typed input values and serializes
results. Show captures also verify the original observable entry order.

Each stdin request is (operation,leftEntries,rightEntries,fields,numbers).
An entry is (key,argument,valueKind,numbers); doubles use raw Word64 bits and
strings use Unicode code points. Exif tags use a nonnegative numeric code for
tagOfWord16 or -code-1 for an explicit TagUnknown constructor. Fields are
(tag,type,count,offset,valueKind,numbers), as in the TIFF binary oracle.

--file INPUT OUTPUT decodes a retained original JPEG with metadata and re-encodes
it at quality 90 with those metadata, matching the JPEG metadataReadTest path.
The importer records complete metadata Show and output SHA256/length. The
metadataWriteTest JPEG branch is also captured (the original RGB white converts
to YCbCr 255/128/128). PNG/BMP branches of that test remain outstanding.

The encoder's original Word8 unsafeShiftL with negative counts is exposed by
short ExifUndefined inputs. Results on the pinned GHC/64-bit build are retained;
no claim is made about arbitrary native architectures or unchecked image memory.
