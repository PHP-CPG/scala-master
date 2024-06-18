package de.tubs.ias.scama.job

import com.typesafe.config.Config
import de.tubs.ias.scama.logging.{ErrorMessage, InformationMessage, ScamaLogger}
import wvlet.log.LogSupport
import java.io.{File, OutputStream, PrintStream}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.collection.mutable
import scala.collection.mutable.{Map => MMap, Stack => MStack}

/** Utility companion object of the ThreadWorkManager
  *
  * @author Simon Koch, Malte Wessels
  *
  */
object ThreadedWorkManager extends LogSupport {

  /** based on the provided config and a set of allowed projects create a list of jobs to be executed
    *
    * @param allowlist a list of projcets that are supposed to be included, if empty every project is included
    * @param config the parsed configuration
    * @return a list of jobs to be later on executed
    */
  def createJobs(allowlist: Set[String])(implicit config: Config): Seq[Job] = {
    val inFolderSrc = config.getString("scama.in")
    val folderCpg = config.getString("scama.cpg.out")

    if (!config.getBoolean("scama.cpg.noGeneration")) {
      new File(inFolderSrc)
        .listFiles()
        .filter(_.isDirectory)
        .filter { dir =>
          allowlist.isEmpty || allowlist.contains(dir.getName)
        }
        .toList
        .map(dir => new Job(dir.getName))
    } else {
      ScamaLogger.log(
        InformationMessage(
          s"generating jobs for cpgs in $folderCpg containing ${new File(
            folderCpg).listFiles.count(_.isFile)} files"))
      new File(folderCpg).listFiles
        .filter(f => f.isFile && f.getName.endsWith(".cpg"))
        .map(x => x.getName.dropRight(4)) // remove '.cpg'
        .filter(name => allowlist.isEmpty || allowlist.contains(name))
        .toList
        .map(new Job(_))
    }
  }
}

/** Class to manage multiple worker running in parallel and executing cmd command
  *
  * @param worker
  */
class ThreadedWorkManager(worker: Int) extends LogSupport {

  /** the table of currently running jobs
    */
  private val jobTable: MMap[Thread, (String, String)] = MMap()

  /** format directive for timestamps
    */
  private val sdf2: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

  /** update the status of a job currently executing
    *
    * this does not notify the manager
    *
    * @param thread the thread of the job that wants a status update
    * @param status the status to which to update the job
    */
  def updateJobStatus(thread: Thread, status: String): Unit = {
    this.synchronized {
      assert(jobTable.contains(thread),
             s"the thread $thread does not exist in the job table")
      val (start, _) = jobTable(thread)
      jobTable.addOne((thread, (start, status)))
    }
  }

  /** update the status of a job currently executing to done
    *
    * this notifies the manager
    *
    * @param thread the thread to update
    */
  def setDone(thread: Thread): Unit = {
    this.synchronized {
      assert(jobTable.contains(thread),
             s"the thread $thread does not exist in the job table")
      val (start, _) = jobTable(thread)
      jobTable.addOne((thread, (start, "done")))
      this.notify()
    }
  }

  /** start work on the provided list of jobs
    *
    * @param jobs the jobs to run
    */
  def startWork(jobs: Seq[Job]): Unit = {
    try {
      val workStack: MStack[Job] = MStack.from(jobs)
      this.synchronized {
        while (workStack.nonEmpty || jobTable.nonEmpty) {
          while (jobTable.size < worker && workStack.nonEmpty) {
            val job = workStack.pop()
            job.setDoneNotifier(this)
            job.start()
            val start = LocalDateTime.now()
            jobTable.addOne((job, (sdf2.format(start), "started")))
            ScamaLogger.log(
              InformationMessage(s"remaining jobs ${workStack.size}"))
          }
          this.wait()
          val msg = giveOverview()
          ScamaLogger.log(
            InformationMessage(s"Currently running worker:\n$msg").noTelegram)
          val forRemoval = jobTable.filter(_._2._2 == "done").map {
            case (thread, _) =>
              thread.join()
              thread
          }
          forRemoval.foreach(jobTable.remove)
        }
      }
    } finally {
      if (jobTable.nonEmpty) {
        ScamaLogger.log(ErrorMessage(
          s"After finishing main loop job table is not empty\n${giveOverview()}"))
        jobTable.foreach(_._1.interrupt())
      }
    }
  }

  /** generate a neat overview of the currently running jobs and their status
    *
    * @return the stringyfied status overview
    */
  private def giveOverview(): String = this.synchronized {
    jobTable
      .map {
        case (thread, jobDescription) =>
          s" ${thread.getName} since ${jobDescription._1} : ${jobDescription._2}"
      }
      .mkString("\n")
  }

  def stringifyErrorStack(x: Throwable): String = {
    val str: mutable.StringBuilder = new mutable.StringBuilder()
    val printTo = new PrintStream(new OutputStream {
      override def write(b: Int): Unit = {
        str.addOne(b.toChar)
      }
      override def toString: String = str.toString()
    })
    x.printStackTrace(printTo)
    str.toString()
  }

}
