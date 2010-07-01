/* sbt Scalariform plugin
 * Copyright 2010 Olivier Michallat
 */
package com.github.olim7t.sbtscalariform

import sbt._
import scala.io.Source
import sbt.Fork.ForkScala
import java.io.File

trait ScalariformPlugin extends BasicScalaProject with SourceTasks {
	import ScalariformPlugin._

	// Use a custom private configuration to retrieve the binaries without
	// leaking the dependency to the client project.
	private val sfConfig = config("sfConfig") hide
	private val sfDep = "com.github.mdr" % "scalariform.core" % "0.0.4" % "sfConfig" from "http://scalariform.googlecode.com/svn/trunk/update-site/plugins/scalariform.core_0.0.4.201006281921.jar"
	private def sfClasspath: Option[String] = {
		val jarFinder = descendents(configurationPath(sfConfig), "*.jar")
		if (jarFinder.get.isEmpty) None else Some(jarFinder.absString)
	}

	private def sfScalaJars = {
		val si = getScalaInstance(ScalariformScalaVersion)
		si.libraryJar :: si.compilerJar :: Nil
	}

	def scalariformOptions = Seq[ScalariformOption]()

	lazy val formatSources = formatSourcesAction
	lazy val formatTests = formatTestsAction

	def sourceTimestamp = "sources.lastFormatted"
	def testTimestamp = "tests.lastFormatted"

	def formatSourcesAction = forAllSourcesTask(sourceTimestamp from mainSources)(runFormatter) describedAs("Format main Scala sources")
	def formatTestsAction = forAllSourcesTask(testTimestamp from testSources)(runFormatter) describedAs("Format test Scala sources")

	override def compileAction = super.compileAction dependsOn(formatSources)
	override def testCompileAction = super.testCompileAction dependsOn(formatTests)

	private def runFormatter(sources: Iterable[Path]): Option[String] = sfClasspath match {
		case None => Some("Scalariform jar not found. Please run update.")
		case Some(cp) =>
			val forkFormatter = new ForkScala(ScalariformMainClass)
			val options = scalariformOptions.map(_.asArgument)
			for (source <- sources) {
				log.debug("Formatting " + source)
				forkFormatter(None, Seq("-cp", cp) , sfScalaJars, options ++ Seq("-i", source.absolutePath), log) 
			}
			None
	}
}
object ScalariformPlugin {
	/** The version of Scala used to run Scalariform.*/
	val ScalariformScalaVersion = "2.8.0.RC6"

	val ScalariformMainClass = "scalariform.commandline.Main"
}
