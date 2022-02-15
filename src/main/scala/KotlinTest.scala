package sbt

import sbt.Keys.*
import sbt.internal.inc.classpath.ClasspathUtil
import sbt.internal.inc.*
import sbt.internal.inc.caching.ClasspathCache
import sbt.internal.inc.classfile.JavaAnalyze
import sbt.util.InterfaceUtil
import xsbti.{VirtualFile, VirtualFileRef}
import xsbti.compile.*

object KotlinTest {
  private object EmptyLookup extends Lookup {
    def changedClasspathHash: Option[Vector[FileHash]] = None

    def analyses: Vector[CompileAnalysis] = Vector.empty

    def lookupOnClasspath(binaryClassName: String): Option[VirtualFileRef] =
      None

    def lookupAnalysis(binaryClassName: String): Option[CompileAnalysis] = None
    def changedBinaries(
      previousAnalysis: xsbti.compile.CompileAnalysis
    ): Option[Set[VirtualFileRef]] = None
    def changedSources(
      previousAnalysis: xsbti.compile.CompileAnalysis
    ): Option[xsbti.compile.Changes[VirtualFileRef]] = None
    def removedProducts(
      previousAnalysis: xsbti.compile.CompileAnalysis
    ): Option[Set[VirtualFileRef]] = None
    def shouldDoIncrementalCompilation(
      changedClasses: Set[String],
      analysis: xsbti.compile.CompileAnalysis
    ): Boolean = true
    override def hashClasspath(
      classpath: Array[VirtualFile]
    ): java.util.Optional[Array[FileHash]] = java.util.Optional.empty()
  }

  val kotlinTests = Def.task {
    val converter = PlainVirtualFileConverter.converter
    def fileToVirtualFile(file: File): VirtualFile =
      converter.toVirtualFile(Path(file.getPath).asPath)

    val out = ((Test / target).value ** "scala-*").get.head / "test-classes"
    val sources: Seq[VirtualFile] =
      ((Test / sourceDirectory).value ** "*.kt").get
        .map(f => fileToVirtualFile(f))
    val xs = (out ** "*.class").get.map(f => Path(f.getPath).asPath)

    val loader =
      ClasspathUtil.toLoader((Test / fullClasspath).value.map(_.data))
    val log = streams.value.log
    val output = new SingleOutput {
      def getOutputDirectory: File = out
    }

    val incrementalCompilerOptions = incOptions.value
    val lookup = incrementalCompilerOptions.externalHooks().getExternalLookup

    def doHash: Array[FileHash] =
      ClasspathCache.hashClasspath(
        sources.map(cp => converter.toPath(cp)).toSeq
      )

    val classpathHash =
      if (lookup.isPresent) {
        val computed = lookup.get().hashClasspath(sources.toArray)
        if (computed.isPresent) computed.get() else doHash
      } else doHash

    val compileSetup = MiniSetup.of(
      output, // MiniSetup gets persisted into Analysis so don't use this
      MiniOptions
        .of(
          classpathHash,
          scalacOptions.value.toArray,
          javacOptions.value.toArray
        ),
      scalaInstance.value.actualVersion,
      compileOrder.value,
      incrementalCompilerOptions.storeApis(),
      (extraIncOptions.value map InterfaceUtil.t2).toArray
    )

    def compile(fs: Set[VirtualFile],
                changs: DependencyChanges,
                callback: xsbti.AnalysisCallback,
                clsFileMgr: ClassFileManager): Unit = {

      def readAPI(source: VirtualFileRef,
                  classes: Seq[Class[_]]): Set[(String, String)] = {
        val (apis, mainClasses, inherits) = ClassToAPI.process(classes)
        apis.foreach(callback.api(source, _))
        mainClasses.foreach(callback.mainClass(source, _))
        inherits.map {
          case (from, to) => (from.getName, to.getName)
        }
      }

      JavaAnalyze(xs, sources, log, output, None)(callback, loader, readAPI)
    }

    val a0 = Incremental
      .apply(
        sources.toSet,
        converter,
        EmptyLookup,
        Analysis.Empty,
        incrementalCompilerOptions,
        compileSetup,
        reusableStamper.value,
        output,
        JarUtils.createOutputJarContent(output),
        None,
        None,
        None,
        log
      )(compile)
      ._2
    val frameworks = (Test / loadedTestFrameworks).value.values.toList
    log.info(s"Compiling ${sources.size} Kotlin test sources to $out...")
    Tests.discover(frameworks, a0, log)._1
  }
}
