import sbt._, Keys._, Path._

object ProjectDefinition extends Build {
  lazy val root = Project("xsbt-plugin-deployer", file(".")).settings(extraSettings:_*)
  lazy val launcher = Project("launcher", file("./launcher")) settings (
    publishTo <<= (version) { version: String =>
      val cloudbees = "https://repository-belfry.forge.cloudbees.com/private/"
      if (version.trim.endsWith("SNAPSHOT")) Some("snapshots" at cloudbees + "snapshots/") 
      else                                   Some("releases"  at cloudbees + "releases/")
    },
    credentials += Credentials(file("/private/belfry/.credentials/.credentials"))
  )

  def extraSettings = Seq(
    generateLauncherBinariesSettings,
    (sbtPlugin := true)
  )


  def generateLauncherBinariesSettings = sourceGenerators in Compile <+=
                                         (sourceManaged in Compile, streams, compile in Compile in launcher,
                                         classDirectory in Compile in launcher) map {(sourceManaged, s, _, launcherClassDir) =>
    import s.log

    val classes = launcherClassDir * "*.class"
    log.info("Classes from " + launcherClassDir.absolutePath + ":" + classes)
    val loadedClasses = classes.get.map(c => c.getName -> IO.readBytes(c))
    val srcCode = createLauncherBinariesScr(loadedClasses)

    val outputFile = sourceManaged/"LauncherBinaries.scala"
    IO.write(outputFile, srcCode, java.nio.charset.Charset.forName("UTF-8"))
    log.info("Wrote " + outputFile)
    Seq(outputFile)
  }

  private def createLauncherBinariesScr(list: Seq[(String, Array[Byte])]): String = {
    val sb = new StringBuilder("package com.belfry.deployer\n\n")
    sb.append("object LauncherBinaries {\n")
    sb.append("  val classes = new scala.collection.mutable.ArrayBuffer[(String, Array[Byte])]()\n")
    for (entry <- list) {
      sb.append("  lazy val `" + entry._1 + "` = Array(" + entry._2.mkString(", ") + ").map(_.toByte)\n")
      sb.append("  classes += \"" + entry._1 + "\" -> `" + entry._1 + "`\n\n")
    }
    sb.append("}")

    sb.toString
  }
}
