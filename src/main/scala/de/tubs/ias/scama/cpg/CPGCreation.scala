package de.tubs.ias.scama.cpg

import de.tubs.ias.scama.utility.EasyProcess
import de.tubs.ias.scama.utility.EasyProcess.EasyProcessResult
import wvlet.log.LogSupport

/** Throwable representing a non zero exit code during cpg creation
  *
  * @author Simon Koch
  *
  * @param path the root path for the php project
  */
class CpgCreationFailure(path: String) extends Throwable {
  override def getMessage: String = s"cpg creation for $path exit with non-zero"
}

/** Throwable representing a timeout during CPG creation
  *
  * @author Simon Koch
  *
  * @param path the root path for the php project
  */
class CpgCreationTimeout(path: String) extends Throwable {
  override def getMessage: String = s"cpg creation timed out for $path"
}

/** case class representation of cpg creation configuration values
  *
  * @author Simon Koch
  *
  * @param cpgGeneratorJa the jar to run
  * @param jvmOps the java runtime environment values
  * @param config the cpg creation config required for the generator
  * @param timeout the timeout of the creation in milliseconds
  * @param out the out file to write the cpg into
  * @param phpVersion the php version for which to create the cpg
  * @param log the log file to log into (todo: not used?)
  */
case class CpgCreationConfig(cpgGeneratorJa: String,
                             jvmOps: String,
                             config: String,
                             timeout: Long,
                             out: String,
                             phpVersion: String,
                             log: String)

/** API wrapper to create a cpg for a given project root
  *
  * @author Simon Koch
  *
  */
object CPGCreation extends LogSupport {

  /** create cpg for project root using the provided configuration
    *
    * @param config the configuration to use
    * @param projectRoot the root folder of the PHP project to convert
    * @return the out file path (i.e., the path to the cpg binary)
    */
  def createCpg(config: CpgCreationConfig, projectRoot: String): String = {
    val out = config.out
    val jar = config.cpgGeneratorJa
    val jvmOps = config.jvmOps
    val cpgConfigFile = config.config
    val phpVersion = config.phpVersion
    val command = List(jar,
                       "--",
                       projectRoot,
                       "-f",
                       "-o",
                       out,
                       "-c",
                       cpgConfigFile,
                       "bytecode",
                       phpVersion)
    (try {
      EasyProcess.run(command, config.timeout, "JAVA_OPTS" -> jvmOps, "LANG" -> "en_US.UTF-8")
    } catch {
      case _: EasyProcess.EasyProcessTimeoutException =>
        throw new CpgCreationTimeout(projectRoot)
    }) match {
      case EasyProcessResult(code, _, _) =>
        if (code != 0) {
          throw new CpgCreationFailure(projectRoot)
        } else {
          out
        }
    }
  }

}
