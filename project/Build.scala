import sbt._, Keys._, Path._

object Build extends Build {
  lazy val root = Project("root", file(".")) settings(extraSettings:_*)

  def extraSettings = Seq(
    generateLauncherBinariesSettings,
    (sbtPlugin := true)
  )

  lazy val generateLauncherBinaries = InputKey[Unit]("generate-launcher-binaries")
  def generateLauncherBinariesSettings = generateLauncherBinaries <<=
                                         sbt.inputTask((_, streams, state) map {(args, s, state) =>
    import s.log
    if (args.isEmpty) throw new Incomplete(Some("No folder specified"))
    else {
      val classes = file(args(0)) * "*.class"
      log.info("Classes:" + classes)
      val loadedClasses = classes.get.map(c => c.getName -> IO.readBytes(c))
      val srcCode = createLauncherBinariesScr(loadedClasses)

      val extracted = Project.extract(state)
      val mainScalaSourcePath = extracted.get(scalaSource in Compile)

      val outputFile = mainScalaSourcePath/"LauncherBinaries.scala"
      IO.write(outputFile, srcCode, java.nio.charset.Charset.forName("UTF-8"))
    }
  })

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