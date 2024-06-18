package de.tubs.ias.scama.job

import com.typesafe.config.Config
import de.tubs.ias.scama.cpg.{
  CPGCreation,
  CpgCreationConfig,
  CpgCreationFailure,
  CpgCreationTimeout
}
import de.tubs.ias.scama.logging._
import de.tubs.ias.scama.utility.EasyProcess
import de.tubs.ias.scama.utility.EasyProcess.EasyProcessResult
import wvlet.log.LogSupport
import java.io.File
import scala.sys.process._

/*object Job {

  object jobLog

  def logJobToFile(logFile: String,
                   project: String,
                   msg: String,
                   success: Boolean): Unit = {
    jobLog.synchronized {
      val fp = new FileWriter(logFile, true)
      try {
        val ts = java.time.LocalDateTime.now().toString
        fp.write(s"[$ts]{$project}[$success] $msg\n")
      } finally {
        fp.flush()
        fp.close()
      }
    }
  }

}*/

/**
  *
  * @author Simon Koch, Malte Wessels
  *
  * @param projectName the name of the project to run a consumer job on
  * @param config the config to run consumer jobs with
  */
class Job(projectName: String)(implicit config: Config)
    extends Thread
    with LogSupport {

  /** the root folder of the project source code to run the consumer on
    */
  private val phpProjectRoot
    : String = config.getString("scama.in") + "/" + projectName + "/"

  /** the path to the created cpg
    */
  private var cpgPath: Option[String] = None

  /** the postfix to use for cpg creation reports
    */
  private val cpgReportPostfix: String =
    config.getString("scama.cpg.reportPostfix")

  /** the thread worker manager that needs to get the done notification
    */
  private var doneNotifier: Option[ThreadedWorkManager] = None

  /** setting the done notifier
    *
    * @param obj the thread manager that needs to get the done notification
    */
  def setDoneNotifier(obj: ThreadedWorkManager): Unit = doneNotifier = Some(obj)

  setName(projectName)

  /** get the name of the project that the consumer is running on in this job
    *
    * @return the name of the project
    */
  def getProjectName: String = projectName

  /** extracts the cpg creation configuration from the main config
    *
    * @return the extracted CpgCreationConfig
    */
  private def getCpgCreationConfig: CpgCreationConfig = {
    CpgCreationConfig(
      config.getString("scama.cpg.run"),
      config.getString("scama.cpg.jvmops"),
      config.getString("scama.cpg.config"),
      config.getLong("scama.cpg.timeout"),
      s"${config.getString("scama.cpg.out")}/$projectName.cpg",
      config.getString("scama.cpg.phpVersion"),
      s"${config.getString("scama.cpg.out")}/cpgCreation.log",
    )
  }

  /** create new cpg based on the configuration for the project root directory
    *
    * @return a boolean indicating whether the creation worked
    */
  private def createCpg(): Boolean = {
    val cpgCreationConfig = getCpgCreationConfig
    // if there is already a cpg
    if (new File(cpgCreationConfig.out).exists()) {
      ScamaLogger.log(JobInfoMessage(projectName, s"using preexisting cpg"))
      // use the cpg instead of creating a new one
      cpgPath = Some(cpgCreationConfig.out)
      true
    } else {
      try {
        ScamaLogger.log(JobCpgCreationStartMessage(projectName))
        cpgPath = Some(CPGCreation.createCpg(cpgCreationConfig, phpProjectRoot))
        ScamaLogger.log(JobCpgCreationSuccessMessage(projectName))
        true
      } catch {
        case x: CpgCreationFailure =>
          ScamaLogger.log(
            JobCpgCreationFailureMessage(projectName, x.getMessage))
          s"rm ${cpgCreationConfig.out}".!!
          false
        case _: CpgCreationTimeout =>
          ScamaLogger.log(JobCpgCreationFailureMessage(projectName, "timeout"))
          s"rm ${cpgCreationConfig.out}".!!
          false
      }
    }
  }

  /** ensure that a cpg is available be it by creation or reusage
    *
    */
  private def takeCareOfCpgCreation(): Unit = {
    if (config.getBoolean("scama.cpg.noGeneration")) {
      // we use an existing cpg from the folder
      val cpgCreationConfig = getCpgCreationConfig
      val cpgFile = new File(cpgCreationConfig.out)
      assert(cpgFile.exists())
      cpgPath = Some(cpgCreationConfig.out)
    } else {
      if (!createCpg()) {
        doneNotifier.get.updateJobStatus(this, "cpg creation failed")
        ScamaLogger.log(JobFailureMessage(projectName, "cpg creation failed"))
      }
    }
  }

  /** start a thread for the job
    *
    */
  override def run(): Unit = {
    ScamaLogger.log(JobStartMessage(projectName))
    assert(doneNotifier.nonEmpty, "you have to set the threaded work manager")
    try {
      takeCareOfCpgCreation()
      val result: EasyProcessResult = executeJob()
      if (!config.getBoolean("scama.cpg.keep")) {
        destroyCpg()
      }
      if (result.code == 0) {
        doneNotifier.get.updateJobStatus(this, "finished successfully")
        ScamaLogger.log(JobSuccessfulEndMessage(projectName))
      } else {
        doneNotifier.get.updateJobStatus(this, "finished with non zero")
        ScamaLogger.log(
          JobFailureMessage(projectName, s"return value was ${result.code}"))
      }

    } catch {
      case x: Throwable =>
        doneNotifier.get.updateJobStatus(this, "unrecoverable error")
        ScamaLogger.log(
          JobFailureMessage(
            projectName,
            s"unrecoverable error ${x.getClass.getName} ${x.getMessage}"))
      // x.printStackTrace()
    } finally {
      doneNotifier.get.setDone(this)
    }
  }

  /** destroy the used cpg
    *
    */
  private def destroyCpg(): Unit = {
    cpgPath match {
      case Some(value) =>
        s"rm $value".!!
        s"rm $value.$cpgReportPostfix".!!
      case None =>
        ScamaLogger.log(
          JobInfoMessage(
            projectName,
            "trying to destroy a cpg that was apparently never created"))
    }
  }

  /** run the consumer against the created cpg
    *
    * @return the process result value
    */
  private def executeJob(): EasyProcessResult = {
    ScamaLogger.log(JobConsumerStartMessage(projectName))
    cpgPath match {
      case Some(value) =>
        val resultFile =
          s"${config.getString("scama.out")}/$projectName.${config.getString("scama.consumer.outFileType")}"
        val parameter = config
          .getString("scama.consumer.parameter")
          .replace("{cpg}", value)
          .replace("{out}", resultFile)
        val command = List(config.getString("scama.consumer.run")) ++ parameter
          .split(" ")
        ScamaLogger.log(InformationMessage(command.mkString(" ")).noTelegram)
        (try {
          EasyProcess.run(command, config.getLong("scama.consumer.timeout"))
        } catch {
          case e: EasyProcess.EasyProcessTimeoutException =>
            doneNotifier.get.updateJobStatus(this, s"timeout of consumer")
            ScamaLogger.log(
              JobConsumerFailureMessage(projectName,
                                        s"timeout of consumer $command"))
            EasyProcessResult(-1, e.output, e.error)
        }) match {
          case r: EasyProcessResult =>
            if (r.code == 0)
              ScamaLogger.log(JobConsumerSuccessMessage(projectName))
            r
        }
      case None =>
        val msg = "trying to work a job for a cpg that was never created"
        ScamaLogger.log(JobConsumerFailureMessage(projectName, msg))
        EasyProcessResult(1, "", msg)
    }
  }

}
