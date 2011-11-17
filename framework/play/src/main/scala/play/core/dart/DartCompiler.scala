package play.core.dart

import com.google.common.io.CharStreams
import com.google.dart.compiler.ast.DartUnit
import com.google.dart.compiler._
import com.google.dart.compiler.{ DartCompiler => Compiler }
import com.google.dart.compiler.CommandLineOptions.CompilerOptions
import java.io._
import play.api._

object DartCompiler {
  def compile(source: File): (String, Seq[File]) = {

    val options = new CompilerOptions()
    var config: CompilerConfiguration = new DefaultCompilerConfiguration(options);

    val outputDirectory = config.getOutputDirectory
    val provider = new DefaultDartArtifactProvider(outputDirectory)

    val listener = new DartCompilerListener {

      var errors = List[CompilationError]()

      override def onError(event: DartCompilationError) {
        errors ::= CompilationError(event.getMessage, event.getLineNumber - 1, event.getColumnNumber - 1)
      }

      override def unitCompiled(unit: DartUnit) {}
    }

    // Compile the Dart application and its dependencies.
    val lib = new UrlLibrarySource(source);
    config = new DelegatingCompilerConfiguration(config) {
      override def expectEntryPoint = true
    }

    try {

      Compiler.compileLib(lib, config, provider, listener) match {
        case null =>
          val r = provider.getArtifactReader(lib, "", config.getBackends.get(0).getAppExtension);
          val js = CharStreams.toString(r);
          (js, Seq(source))
        case _ =>
          val err = listener.errors.head
          throw CompilationException(err.message, source, err.atLine, err.atColumn)
      }
    } catch {
      case e: IOException =>
        throw PlayException("Compilation error", e.getMessage)
    }

  }
}

case class CompilationError(message: String, atLine: Int, atColumn: Int)

case class CompilationException(message: String, dartFile: File, atLine: Int, atColumn: Int)
  extends PlayException("Compilation error", message) with PlayException.ExceptionSource {
  def line = Some(atLine)
  def position = Some(atColumn)
  def input = Some(scalax.file.Path(dartFile))
  def sourceName = Some(dartFile.getAbsolutePath)
}
