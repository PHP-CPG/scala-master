package de.tubs.ias.scama.logging

import wvlet.log.LogSupport
import java.io.{File, FileWriter}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.collection.mutable.{ListBuffer, Map => MMap}
import scala.io.Source

trait LogLine {

  protected val lines: ListBuffer[String] = ListBuffer()
  def getLines: List[String] = lines.toList
  protected def parseTimestamp(ts: String): LocalDateTime = {
    DateTimeFormatter
      .ofPattern("yyyy-MM-dd/HH:mm:ss")
      .parse(ts)
      .asInstanceOf[LocalDateTime]
  }
  def addLine(line: String): LogLine = {
    lines.addOne(line)
    this
  }
  def process(): LogLine
}

class GenericLogLine(ts: String, msg: String) extends LogLine {

  def getTs: String = ts

  def getMsg: String = msg

  override def process(): LogLine = {
    this
  }
}

class JobLogLine(ts: String, name: String, sub: Option[String], msg: String)
    extends LogLine {

  def getTs: String = ts

  def getJobName: String = name

  def getSub: Option[String] = sub

  def getMsg: String = msg

  override def process(): LogLine = {
    this
  }

}

class JobSummary(loglines: List[JobLogLine]) {

  def getLogLines: List[String] = loglines.flatMap(_.getLines)

  def getJobName: String = loglines.head.getJobName

  def cpgSuccess: Boolean = !loglines.exists { line =>
    line.getSub.getOrElse("no sub") == "cpg" &&
    line.getMsg.contains("failed")
  }

  def cpgTimeout: Boolean = !cpgSuccess && loglines.exists { line =>
    line.getSub.getOrElse("no sub") == "cpg" &&
    line.getMsg.contains("timeout")
  }

  /**
   * subclass of !consumerSucess
   *
   * @return if the job failed because there was no cpg
   */
  def consumerNoCpg: Boolean = loglines.exists(line =>
    line.getSub.getOrElse("no sub") == "consumer"
      && line.getMsg.contains("failed due to trying to work a job for a cpg that was never created"))

  def consumerSuccess: Boolean = !loglines.exists { line =>
    line.getSub.getOrElse("no sub") == "consumer" &&
    line.getMsg.contains("failed")
  }

  def consumerTimeout: Boolean = !consumerSuccess && loglines.exists { line =>
    line.getSub.getOrElse("no sub") == "consumer" &&
    line.getMsg.contains("timeout")
  }

  def success: Boolean = {
    loglines.last.getLines.head.contains("finished")
  }

}

class LogEvaluator(log: String) extends LogSupport {

  val genericLines: ListBuffer[GenericLogLine] = ListBuffer()
  val jobSpecificLines: MMap[String, ListBuffer[JobLogLine]] = MMap()

  private def addFinishedLines(ll: LogLine): Unit = {
    ll match {
      case line: GenericLogLine => genericLines.addOne(line)
      case line: JobLogLine =>
        if (jobSpecificLines.contains(line.getJobName)) {
          jobSpecificLines(line.getJobName).addOne(line)
        } else {
          jobSpecificLines.addOne(line.getJobName -> ListBuffer(line))
        }
      case x => error(s"unknown log line class ${x.getClass.getName}")
    }
  }

  def run(): LogEvaluator = {
    val source = Source.fromFile(log)
    try {
      val lineIterator = source.getLines()
      var current: LogLine =
        new GenericLogLine("wayne", "wayne").addLine(lineIterator.next())
      lineIterator.foreach { line =>
        //println(line.split("\\[|\\]").toList)
        line.split("\\[|\\]").toList match {
          case _ :: ts :: msg :: Nil =>
            addFinishedLines(current.process())
            current = new GenericLogLine(ts, msg).addLine(line)
          case _ :: ts :: _ :: job :: msg :: Nil =>
            addFinishedLines(current.process())
            current = new JobLogLine(ts, job, None, msg).addLine(line)
          case _ :: ts :: _ :: job :: _ :: sub :: msg :: Nil =>
            addFinishedLines(current.process())
            current = new JobLogLine(ts, job, Some(sub), msg).addLine(line)
          case _ =>
            current.addLine(line)
        }
      }
    } finally {
      source.close()
    }
    this
  }

  private def summarizeJobs(): List[JobSummary] = {
    this.jobSpecificLines.map {
      case (_, value) => new JobSummary(value.toList)
    }.toList
  }

  def getJobSummary(folder: Option[String]): LogEvaluator = {
    info(s"generic log entries: ${this.genericLines.size}")
    info(s"jobs with corresponding logs: ${this.jobSpecificLines.size}")
    val jobSummaries: List[JobSummary] = summarizeJobs()
    info(s"jobs succeeded     : ${jobSummaries.count(_.success)}")
    info(s"jobs failed        : ${jobSummaries.count(!_.success)}")
    info(s"... due to cpg     : ${jobSummaries.count(!_.cpgSuccess)}")
    info(s"....... timeout    : ${jobSummaries.count(_.cpgTimeout)}")
    info(s"... due to consumer: ${jobSummaries.count(!_.consumerSuccess)}")
    info(s"....... no cpg     : ${jobSummaries.count(_.consumerNoCpg)}")
    info(s"....... timeout    : ${jobSummaries.count(_.consumerTimeout)}")
    folder match {
      case Some(value) =>
        val folderCpg = new File(value + "/cpg/")
        val folderConsumer = new File(value + "/consumer/")
        folderCpg.mkdirs()
        folderConsumer.mkdirs()
        jobSummaries.filter(!_.success).foreach { job =>
          val fw = if (!job.cpgSuccess) {
            new FileWriter(new File(folderCpg.getPath + s"/${job.getJobName}"))
          } else {
            new FileWriter(
              new File(folderConsumer.getPath + s"/${job.getJobName}"))
          }
          try {
            job.getLogLines.foreach(line => fw.write(line + "\n"))
          } finally {
            fw.close()
          }
        }
      case None =>
    }
    this
  }

}
