package com.belfry.deployer
import sbt._, Keys._, Path._, Load._

object DeployerPlugin extends Plugin {dp =>

  override lazy val settings = Seq(proguardScriptS, proguardConfS, proguardS,
                                   proguard <<= proguard.dependsOn(packageBin in Compile)) ++ deployS ++ deployAppletS

  val proguardScript = SettingKey[String]("proguard-script", "Location for the proguardy script")
  private def proguardScriptS = proguardScript := "proguard.sh"

  val proguardConf = SettingKey[java.io.File]("proguard-conf", "Location for the proguard configuration file")
  private def proguardConfS = (proguardConf <<= baseDirectory {_/"proguard.pro"})

  private def forAllProjects[R](project: ProjectRef, bs: BuildStructure)(code: ProjectRef => Option[R]): Seq[R] = {
    def recurse(p: ProjectRef): Seq[R] = {
      code(p) map {ap =>
        Project.getProject(p, bs) map (rp => ap +: (rp.referenced flatMap recurse)) getOrElse Seq.empty
      } getOrElse Seq.empty
    }
    recurse(project)
  }

  private def getDepsJars(project: ProjectRef, bs: BuildStructure) = forAllProjects(project, bs) {p =>
    artifactPath in Compile in packageBin in p get bs.data
  }

  val proguard = TaskKey[Unit]("proguard")
  private def proguardS = proguard <<= (streams, fullClasspath in Compile,
                                        artifactPath in (Compile, packageBin),
                                        proguardScript, proguardConf,
                                        thisProjectRef, buildStructure) map {(s, classpath, jarPath, proguardScript, proguardConf, tpr, structure) =>
    import s.log

    val absoluteIns =(for (file <- classpath.map(_.data) ++ getDepsJars(tpr, structure) if file.getName.endsWith(".jar"))
                       yield "-injars " + file.getAbsolutePath + (if (file != jarPath) "(!META-INF/*)" else "")).mkString("\n")
//     log.info("Excluded: " + proguardExclude)
    log.info("Injars: " + absoluteIns)
    val outJar = new java.io.File(jarPath.getParentFile, jarPath.getName.dropRight(4) + "-shrinked.jar").getAbsolutePath
    log.info("Output: " + outJar)
    log.info("Proguard Conf: " + proguardConf.getAbsolutePath)
    try
      Process(Seq(proguardScript, absoluteIns, "-outjars " + outJar, "@" + proguardConf.getAbsolutePath))! match {
        case 0 =>
        case _ => throw new Incomplete(Some("Proguard returned non cero exit code"))
      }
    catch {case ex => throw new Incomplete(Some(ex.getMessage))}
  }

  val deploy = TaskKey[Unit]("deploy", "Invoke first proguard, then generate an launcher jar with the proguarded stuff in it.")
  private def deployS = Seq(deploy <<= deployTask("Launcher", "-release"), deploy <<= deploy.dependsOn(proguard))
  val deployApplet = TaskKey[Unit]("deploy-applet", "Same as deploy, but generates an applet jar")
  private def deployAppletS = Seq(deployApplet <<= deployTask("AppletLauncher", "-applet-release"), deployApplet <<= deployApplet.dependsOn(proguard))

  def deployTask(mainClass: String, deployedExtension: String): Project.Initialize[sbt.Task[Unit]] =
                                                                  (streams, fullClasspath in Compile,
                                                                  artifactPath in (Compile, packageBin),
                                                                  proguardScript, proguardConf) map {(s, classpath, jarPath, proguardScript, proguardConf) =>
    import s.log

    val outputPath = jarPath.getParentFile
    val artifactBaseName = jarPath.getName.dropRight(4)
    val shrinkedJar = (outputPath/(artifactBaseName + "-shrinked.jar"))
    if (shrinkedJar.asFile.exists) {
      val packedApp = (outputPath/ (artifactBaseName + ".packed.gz"))
      import java.util.jar._
      import java.io._

      log.info("Packing")
      Process(Seq("pack200", packedApp.absolutePath, shrinkedJar.absolutePath))!!

      log.info("Creating release jar")
      val outputJar = (outputPath/(artifactBaseName + deployedExtension + ".jar"))


      val manifest  = new Manifest()
      manifest.getMainAttributes.putValue("Main-Class", "Launcher")

      val jarOutputStream = new JarOutputStream(new FileOutputStream(outputJar.asFile))
      try {
        def loadClass(name: String) = DeployerPlugin.getClass.getResourceAsStream(name)
        jarOutputStream.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"))
        jarOutputStream.write(("Main-Class: " + mainClass + "\n").getBytes)


        for (clazz <- LauncherBinaries.classes) {
          jarOutputStream.putNextEntry(new JarEntry(clazz._1))
          jarOutputStream.write(clazz._2)
        }

        val appEntry = new JarEntry("app")
        appEntry.setMethod(java.util.zip.ZipEntry.DEFLATED)
        jarOutputStream.putNextEntry(appEntry)
        IO.transferAndClose(new FileInputStream(packedApp.asFile), jarOutputStream)
        jarOutputStream.finish()
      } finally {
        jarOutputStream.close()
      }
      packedApp.asFile.delete()
    } else throw new Incomplete(Some("NO SHRINKED JAR"))
  }
}
