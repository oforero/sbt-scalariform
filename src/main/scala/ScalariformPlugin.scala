/* sbt Scalariform plugin
 * Copyright 2010 Olivier Michallat
 */
package com.github.olim7t.sbtscalariform

import sbt._
import sbt.Fork.ForkScala
import scala.io.Source
import java.io.File
import FileUtilities.{Newline, withTemporaryFile, write}

trait ScalariformPlugin extends BasicScalaProject with SourceTasks {

	// Use a custom private configuration to retrieve the binaries without
	// leaking the dependency to the client project.
	private val sfConfig = config("sfConfig") hide
	private val sfDep = "com.github.mdr" % "scalariform.core" % ScalariformPlugin.Version % "sfConfig" from ScalariformPlugin.CoreUrl
	private def sfClasspath: Option[String] = {
		val jarFinder = descendents(configurationPath(sfConfig), "*.jar")
		if (jarFinder.get.isEmpty) None else Some(jarFinder.absString)
	}

	private def sfScalaJars = {
		val si = getScalaInstance(ScalariformPlugin.ScalaVersion)
		si.libraryJar :: si.compilerJar :: Nil
	}

	def scalariformOptions = Seq[ScalariformOption]()

	lazy val formatSources = formatSourcesAction
	lazy val formatTests = formatTestsAction

	def sourceTimestamp = "sources.lastFormatted"
	def testTimestamp = "tests.lastFormatted"

	private val configuredRun = ScalariformPlugin.runFormatter(sfScalaJars, sfClasspath _, scalariformOptions, log) _

	def formatSourcesAction = forAllSourcesTask(sourceTimestamp from mainSources)(configuredRun) describedAs("Format main Scala sources")
	def formatTestsAction = forAllSourcesTask(testTimestamp from testSources)(configuredRun) describedAs("Format test Scala sources")

	override def compileAction = super.compileAction dependsOn(formatSources)
	override def testCompileAction = super.testCompileAction dependsOn(formatTests)
}
object ScalariformPlugin {
	val Version = "0.0.4.201007071230"
	val CoreUrl = "http://scalariform.googlecode.com/svn/trunk/update-site/plugins/scalariform.core_" + Version + ".jar"

	/** The version of Scala used to run Scalariform.*/
	val ScalaVersion = "2.8.0.RC6"

	val MainClass = "scalariform.commandline.Main"

	def runFormatter(scalaJars: List[File], classpath: () => Option[String], options: Seq[ScalariformOption], log: Logger)(sources: Iterable[Path]): Option[String] = classpath() match {
		case None => Some("Scalariform jar not found. Please run update.")
		case Some(cp) =>
			val forkFormatter = new ForkScala(MainClass)
			// Assume InPlace if neither InPlace nor Test are provided
			val finalOpts = if ((options contains InPlace) || (options contains Test)) options else options ++ Seq(InPlace)
			val args = finalOpts.map(_.asArgument)

			// TODO refactor this block of code to make it actually readable
			withTemporaryFile(log, "sbt-scalariform", ".lst") { file =>
				val error = write(file, sources.map(_.absolutePath).mkString(Newline), log) orElse {
					val errorCode = forkFormatter(None, Seq("-cp", cp), scalaJars, args ++ Seq("-l=" + file.getAbsolutePath), log)
					errorCode match {
						case 0 => None
						case n => Some("Scalariform exited with error code " + n)
					}
				}
				error toLeft ()
			} match {
				case Left(error) => Some(error)
				case Right(_) => None
			}
	}
}
