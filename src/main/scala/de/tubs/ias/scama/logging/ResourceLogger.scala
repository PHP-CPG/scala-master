package de.tubs.ias.scama.logging

import sys.process._

/** resource usage log message
  *
  * @author Simon Koch
  *
  * @param cpuUsage the current cpu usage
  * @param ramUsage the current ram usage
  * @param javaProcesses the running java processes
  */
case class ResourceUsageMessage(cpuUsage: Double,
                                ramUsage: Int,
                                javaProcesses: Map[Int, String])
    extends Message {

  /** do not send resource usage via telegram
    */
  telegram = false

  /** get the resource usage as a neat string to be printed
    *
    * @return the neat string summarizing the resource usage
    */
  override def getLogMessageContent: String = {
    val sb: StringBuilder = new StringBuilder()
    sb.append(s"CPU: ${cpuUsage * 100} %, ")
    sb.append(s"RAM: $ramUsage MB\n")
    sb.append(
      javaProcesses
        .map {
          case (pid, main) => s"$pid => $main"
        }
        .mkString("\n"))
    sb.toString()
  }
}

/** logger utility to keep track of the resource usage of the host system
  *
  * @author Simon Koch
  *
  * @param interval the interval in ms in which to log the resource usage
  */
class ResourceLogger(interval: Long) extends Thread {

  /** whether the resource logger should still be running
    */
  private var running: Boolean = true

  /** stop the resource logging process
    *
    */
  def stopResourceLogger(): Unit = this.synchronized {
    running = false
    this.notify()
  }

  /** run the background thread to keep logging resource usage
    *
    */
  override def run(): Unit = {
    try {
      this.synchronized {
        while (running) {
          ScamaLogger.log(ResourceLogger.getResourceLogMessage)
          this.wait(interval)
        }
      }
    } catch {
      case x: Throwable =>
        InformationMessage(s"Resource logger encountered ${x.getMessage}")
    } finally {
      ScamaLogger.log(InformationMessage("Resource logger shutting down"))
    }
  }
}

/** utility companion object
  *
  * @author Simon Koch
  *
  */
object ResourceLogger {

  /** get current cpu usage
    *
    * @return the cpu usage
    */
  def getCpuUsage: Double = {
    "uptime".!!.split("load average:").last.split(",").head.trim.toDouble
  }

  /** get the current ram usage
    *
    * @return the ram usage in MB (todo: what unit are we returning here?)
    */
  def getRamUsage: Int = {
    val _ :: _ :: used :: _ :: _ :: _ :: _ :: Nil =
      "free -m".!!.split("\n").apply(1).split("[ ]+").toList
    used.toInt
  }

  /** parse a single line outputted by ps
    *
    * @param psLine the line to be processed
    * @return the contained relevant meta data (i.e., pid and corresponding cmd line)
    */
  private def processJavaPsLine(psLine: String): Option[(Int, String)] = {
    val lineSplit = psLine.split(" ").toList.filterNot(_ == "").map(_.trim)
    val index =
      lineSplit.reverse.indexWhere(_.matches("^([a-zA-Z^\\/]+\\.)+[a-zA-Z]+"))
    if (index == -1) {
      None
    } else {
      Some(
        lineSplit.apply(1).toInt -> lineSplit
          .slice(lineSplit.length - index - 1, lineSplit.length)
          .mkString(" "))
    }
  }

  /** get a list of all currently running java processes
    *
    * @return the map of pid => command line
    */
  def getJavaProcesses: Map[Int, String] = {
    ("ps -aux" #| "grep java").!!.split("\n")
      .map(processJavaPsLine)
      .filter(_.nonEmpty)
      .map(_.get)
      .toMap
  }

  /** generate a resource log message of the current resource usage
    *
    * @return the resource usage message
    */
  def getResourceLogMessage: ResourceUsageMessage = {
    ResourceUsageMessage(getCpuUsage, getRamUsage, getJavaProcesses)
  }

}
