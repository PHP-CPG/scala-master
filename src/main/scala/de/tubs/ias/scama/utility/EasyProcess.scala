package de.tubs.ias.scama.utility

import com.zaxxer.nuprocess.{NuAbstractProcessHandler, NuProcess, NuProcessBuilder}
import de.tubs.ias.scama.logging.{InformationMessage, ScamaLogger}

import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.{MapHasAsJava, SeqHasAsJava}

/** API to create new cmd processes for easy killing
  *
  * @author Simon Koch, Malte Wessels
  *
  */
object EasyProcess {

  /** exception representing a timeout for the started process
    *
    * @author Simon Koch
    *
    * @param output the output of stdout
    * @param error the output of stderr
    */
  class EasyProcessTimeoutException(val output: String, val error: String)
      extends Throwable

  /** wrapper class to return the result of a easy process
    *
    * @author Simon Koch
    *
    * @param code the return code of the started process
    * @param stdout the stdout output
    * @param stderr the stderr output
    */
  case class EasyProcessResult(code: Int, stdout: String, stderr: String)

  /** ProcessHandler implementation to keep the stdout and stderr output
    *
    * @author Simon Koch
    *
    */
  private class ProcessHandler() extends NuAbstractProcessHandler {

    /** StringBuilder keeping track of stdout
      */
    private val stdout: StringBuilder = new StringBuilder()

    /** getter to get the (current) state of the stdout output
      *
      * @return
      */
    def getStdout: String = stdout.toString()

    /** StringBuilder keeping track of stderr
      *
      */
    private val stderr: StringBuilder = new StringBuilder()

    /** getter to get the (current) state of the stderr output
      *
      * @return
      */
    def getStderr: String = stderr.toString()

    override def onStart(nuProcess: NuProcess): Unit = super.onStart(nuProcess)

    /** utility function to read bytes from buffer and convert them to a string
      *
      * @param buffer the buffer
      * @param closed flag whether or not the stream was closed
      * @return the string read from the buffer
      */
    private def readString(buffer: ByteBuffer, closed: Boolean): String = {
      if (!closed) {
        val outArray : Array[Byte] = new Array(buffer.remaining())
        buffer.get(outArray)
        outArray.toString
      } else {
        "read closed" //todo: unsure how, when, why this might happen
      }
    }

    /** handler to read and store the stderr output
      *
      * @param buffer written bytes by the process
      * @param closed whether the stream has been closed
      */
    override def onStderr(buffer: ByteBuffer, closed: Boolean): Unit = {
      stderr.append(readString(buffer, closed) + "\n")
    }

    /** handler to read and store the stdout output
      *
      * @param buffer written bytes by the process
      * @param closed whether the stream has been closed
      */
    override def onStdout(buffer: ByteBuffer, closed: Boolean): Unit = {
      stdout.append(readString(buffer, closed) + "\n")
    }

  }

  /** main function to start a given cmd command and handle its runtime and output
    *
    * @param command the command and command line arguments as a list
    * @param timeout the max runtime of the process in milliseconds
    * @param env the environment variables
    * @return
    */
  def run(command: List[String],
          timeout: Long,
          env: (String, String)*): EasyProcessResult = {
    val pb: NuProcessBuilder =
      new NuProcessBuilder(command.asJava, env.toMap.asJava)
    val handler = new ProcessHandler()
    assert(handler != null, "just checking as I got some weird error log entries")
    pb.setProcessListener(handler)
    val process = pb.start()
    // we wait blocking for timeout
    val exit = process.waitFor(timeout, TimeUnit.MILLISECONDS)
    // if the process is still running afterwards
    if (process.isRunning) {
      ScamaLogger.log(InformationMessage(
        s"command ${command.mkString(" ")} reached max execution time .. let's get killin'"))
      // we do a forced kill
      process.destroy(true)
      // ensure that we do not continue until this job is done
      process.waitFor(10000, TimeUnit.MILLISECONDS)
      // this assert is a sanity check as I am not yet trusting NuProcess
      assert(
        !process.isRunning,
        "after force kill and waiting at most 10 seconds the process should be dead ... it ain't")
      // throw the timeout exception for logging/handling by the user
      throw new EasyProcessTimeoutException(handler.getStdout,
                                            handler.getStderr)
    } else {
      // everything peachy ... give back the exit code and stdout and stderr output
      EasyProcessResult(exit, handler.getStdout, handler.getStderr)
    }
  }

}
