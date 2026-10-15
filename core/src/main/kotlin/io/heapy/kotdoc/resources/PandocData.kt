// Text.Pandoc.Data, © 2013–2024 John MacFarlane, GPL-2.0-or-later; licenses/.
package io.heapy.kotdoc.resources

import io.heapy.kotdoc.archive.ZipArchive
import io.heapy.kotdoc.archive.ZipCodec
import io.heapy.kotdoc.archive.ZipDecodeException
import io.heapy.kotdoc.archive.ZipDecodeResult
import io.heapy.kotdoc.errors.PandocError
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.templates.PartialPaths
import io.heapy.kotdoc.translations.TranslationRuntime
import java.math.BigInteger
import java.util.Collections

private fun raw(s: String)=CharacterStringChars.fromString(s)

interface EmbeddedPandocData {
    val names: List<CharacterString>
    fun read(name: CharacterString): ByteArray?
}
object PackagedPandocData : EmbeddedPandocData {
    private const val prefix="/io/heapy/kotdoc/pandoc-data/"
    override val names: List<CharacterString> by lazy {
        val text=checkNotNull(javaClass.getResourceAsStream(prefix+"all-data-index.txt")).bufferedReader(Charsets.UTF_8).use { it.readText() }
        Collections.unmodifiableList(text.lineSequence().filter(String::isNotEmpty).map(::raw).toList())
    }
    private val paths by lazy { names.associateWith { buildString { it.points().forEach(::appendCodePoint) } } }
    override fun read(name: CharacterString): ByteArray? = paths[name]?.let { path ->
        checkNotNull(javaClass.getResourceAsStream(prefix+path)).use { it.readBytes() }
    }
}

/** PandocMonad effects; installed and embedded builds execute the same routing layer. */
interface PandocDataRuntime : TranslationRuntime {
    val filePaths: PartialPaths get()=PartialPaths.System
    /** null selects the original non-EMBED_DATA_FILES branch. */
    val embeddedData: EmbeddedPandocData? get()=PackagedPandocData
    fun readFileStrict(path: CharacterString): ByteArray
    fun readFileLazy(path: CharacterString): ByteArray
    fun fileExists(path: CharacterString): Boolean
    fun getDataFileName(path: CharacterString): CharacterString
    fun lookupEnv(name: String): String?
    fun getCurrentTime(): PandocUtcTime
    override fun readDataFile(file: CharacterString): ByteArray = PandocData.readDataFile(this,file)
}

/** Separate IO boundary for Data's directory/environment operations. */
interface PandocDataDirectories {
    val filePaths: PartialPaths get()=PartialPaths.System
    fun getDataDir(): CharacterString
    fun listDirectory(directory: CharacterString): List<CharacterString>
    fun doesDirectoryExist(directory: CharacterString): Boolean
    fun getXdgDataDirectory(application: String): CharacterString
    fun getAppUserDataDirectory(application: String): CharacterString
}

object PandocData {
    fun checkUserDataDir(runtime: PandocDataRuntime,file: CharacterString): CharacterString? {
        if(!PandocFilePaths.isRelative(file,runtime.filePaths)) return null
        val canonical=PandocFilePaths.canonical(file,runtime.filePaths)
        if(PandocFilePaths.directories(canonical,runtime.filePaths).firstOrNull()==raw("..")) return null
        return runtime.getsCommonState { it.userDataDir }
    }
    fun getTimestamp(runtime: PandocDataRuntime): PandocUtcTime {
        val epoch=runtime.lookupEnv("SOURCE_DATE_EPOCH")?.let(::safeReadInteger)
        return if(epoch!=null) PandocUtcTime.fromPosixPicoseconds(epoch*BigInteger.TEN.pow(12)) else runtime.getCurrentTime()
    }
    fun getPOSIXTime(runtime: PandocDataRuntime): BigInteger = getTimestamp(runtime).posixPicoseconds()
    fun readDataFile(runtime: PandocDataRuntime,file: CharacterString): ByteArray {
        val directory=checkUserDataDir(runtime,file)
        if(directory!=null) {
            val path=PandocFilePaths.combine(directory,file,runtime.filePaths)
            if(runtime.fileExists(path)) return runtime.readFileStrict(path)
        }
        return readDefaultDataFile(runtime,file)
    }
    fun readDefaultDataFile(runtime: PandocDataRuntime,file: CharacterString): ByteArray {
        val kind=when(file) { raw("reference.docx")->"docx";raw("reference.pptx")->"pptx";raw("reference.odt")->"odt";else->null }
        if(kind!=null) return ZipCodec.encode(reference(runtime,kind),runtime.filePaths)
        val embedded=runtime.embeddedData
        if(embedded!=null) return embedded.read(PandocFilePaths.canonical(file,runtime.filePaths))
            ?: throw PandocError.PandocCouldNotFindDataFileError(TextChars.fromPoints(file.points()))
        val relative=if(file==raw("MANUAL.txt")) file else PandocFilePaths.combine(raw("data"),file,runtime.filePaths)
        val path=runtime.getDataFileName(relative)
        if(!runtime.fileExists(path)) throw PandocError.PandocCouldNotFindDataFileError(TextChars.fromPoints(path.points()))
        return runtime.readFileStrict(path)
    }
    private fun reference(runtime: PandocDataRuntime,kind: String): ZipArchive {
        val directory=runtime.getsCommonState { it.userDataDir }
        if(directory!=null) {
            val path=PandocFilePaths.combine(directory,raw("reference.$kind"),runtime.filePaths)
            if(runtime.fileExists(path)) return when(val parsed=ZipCodec.decode(runtime.readFileLazy(path))) {
                is ZipDecodeResult.Success -> parsed.archive
                is ZipDecodeResult.Failure -> throw ZipDecodeException(parsed.error)
            }
        }
        val entries=referencePaths.getValue(kind).map { path ->
            val epoch=floorPicoseconds(if(kind=="docx") getTimestamp(runtime).posixPicoseconds() else getPOSIXTime(runtime))
            val bytes=readDataFile(runtime,raw("$kind/$path"))
            ZipCodec.toEntry(raw(path),epoch,bytes,runtime.filePaths)
        }
        var archive=ZipArchive()
        for(entry in entries.asReversed()) archive=archive.add(entry,runtime.filePaths)
        return archive
    }
    fun getDataFileNames(embedded: EmbeddedPandocData?,directories: PandocDataDirectories): List<CharacterString> {
        val names=if(embedded!=null) embedded.names else {
            val root=PandocFilePaths.combine(directories.getDataDir(),raw("data"),directories.filePaths)
            // DFS preserves IO order; returned paths stay relative to the data root.
            class Prefix(val name: CharacterString,val parent: Prefix?)
            data class Frame(val directory: CharacterString,val prefix: Prefix?,val names: Iterator<CharacterString>)
            val found=mutableListOf<CharacterString>()
            val stack=ArrayDeque<Frame>();stack.addLast(Frame(root,null,directories.listDirectory(root).iterator()))
            while(stack.isNotEmpty()) {
                val frame=stack.last()
                if(!frame.names.hasNext()) { stack.removeLast();continue }
                val name=frame.names.next();val path=PandocFilePaths.combine(frame.directory,name,directories.filePaths)
                if(directories.doesDirectoryExist(path)) stack.addLast(Frame(path,Prefix(name,frame.prefix),directories.listDirectory(path).iterator()))
                else {
                    var relative=name;var prefix=frame.prefix
                    while(prefix!=null) { relative=PandocFilePaths.combine(prefix.name,relative,directories.filePaths);prefix=prefix.parent }
                    found.add(relative)
                }
            }
            listOf(raw("MANUAL.txt"))+found
        }
        return Collections.unmodifiableList((listOf(raw("reference.docx"),raw("reference.odt"),raw("reference.pptx"))+names).sorted())
    }
    fun defaultUserDataDir(directories: PandocDataDirectories): CharacterString {
        // The source catches SomeException only around these two path getters.
        val xdg=try { directories.getXdgDataDirectory("pandoc") } catch(error: Exception) { raw("") }
        val xdgExists=directories.doesDirectoryExist(xdg)
        val legacy=try { directories.getAppUserDataDirectory("pandoc") } catch(error: Exception) { raw("") }
        val legacyExists=directories.doesDirectoryExist(legacy)
        return if(!xdgExists && legacyExists) legacy else xdg
    }
    private val referencePaths=mapOf(
        "docx" to listOf(
            "[Content_Types].xml",
            "_rels/.rels",
            "docProps/app.xml",
            "docProps/core.xml",
            "docProps/custom.xml",
            "word/document.xml",
            "word/fontTable.xml",
            "word/footnotes.xml",
            "word/comments.xml",
            "word/numbering.xml",
            "word/settings.xml",
            "word/webSettings.xml",
            "word/styles.xml",
            "word/_rels/document.xml.rels",
            "word/_rels/footnotes.xml.rels",
            "word/theme/theme1.xml"
        ),
        "odt" to listOf("mimetype","manifest.rdf","styles.xml","content.xml","meta.xml","META-INF/manifest.xml"),
        "pptx" to listOf(
            "[Content_Types].xml",
            "_rels/.rels",
            "docProps/app.xml",
            "docProps/core.xml",
            "ppt/_rels/presentation.xml.rels",
            "ppt/presProps.xml",
            "ppt/presentation.xml",
            "ppt/slideLayouts/_rels/slideLayout1.xml.rels",
            "ppt/slideLayouts/_rels/slideLayout2.xml.rels",
            "ppt/slideLayouts/_rels/slideLayout3.xml.rels",
            "ppt/slideLayouts/_rels/slideLayout4.xml.rels",
            "ppt/slideLayouts/_rels/slideLayout5.xml.rels",
            "ppt/slideLayouts/_rels/slideLayout6.xml.rels",
            "ppt/slideLayouts/_rels/slideLayout7.xml.rels",
            "ppt/slideLayouts/_rels/slideLayout8.xml.rels",
            "ppt/slideLayouts/_rels/slideLayout9.xml.rels",
            "ppt/slideLayouts/_rels/slideLayout10.xml.rels",
            "ppt/slideLayouts/_rels/slideLayout11.xml.rels",
            "ppt/slideLayouts/slideLayout1.xml",
            "ppt/slideLayouts/slideLayout10.xml",
            "ppt/slideLayouts/slideLayout11.xml",
            "ppt/slideLayouts/slideLayout2.xml",
            "ppt/slideLayouts/slideLayout3.xml",
            "ppt/slideLayouts/slideLayout4.xml",
            "ppt/slideLayouts/slideLayout5.xml",
            "ppt/slideLayouts/slideLayout6.xml",
            "ppt/slideLayouts/slideLayout7.xml",
            "ppt/slideLayouts/slideLayout8.xml",
            "ppt/slideLayouts/slideLayout9.xml",
            "ppt/slideMasters/_rels/slideMaster1.xml.rels",
            "ppt/slideMasters/slideMaster1.xml",
            "ppt/slides/_rels/slide1.xml.rels",
            "ppt/slides/slide1.xml",
            "ppt/slides/_rels/slide2.xml.rels",
            "ppt/slides/slide2.xml",
            "ppt/slides/_rels/slide3.xml.rels",
            "ppt/slides/slide3.xml",
            "ppt/slides/_rels/slide4.xml.rels",
            "ppt/slides/slide4.xml",
            "ppt/tableStyles.xml",
            "ppt/theme/theme1.xml",
            "ppt/viewProps.xml",
            "ppt/notesMasters/notesMaster1.xml",
            "ppt/notesMasters/_rels/notesMaster1.xml.rels",
            "ppt/notesSlides/notesSlide1.xml",
            "ppt/notesSlides/_rels/notesSlide1.xml.rels",
            "ppt/notesSlides/notesSlide2.xml",
            "ppt/notesSlides/_rels/notesSlide2.xml.rels",
            "ppt/theme/theme2.xml"
        )
    )
}
