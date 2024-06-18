package de.tubs.ias.scama

import com.typesafe.config.{Config, ConfigFactory}
import de.halcony.argparse.{
  OptionalValue,
  Parser,
  ParsingException,
  ParsingResult
}
import de.tubs.ias.scama.job.ThreadedWorkManager
import de.tubs.ias.scama.logging.{InformationMessage, LogEvaluator, ScamaLogger}
import wvlet.log.LogSupport

import java.io.File
import scala.annotation.nowarn
import scala.io.Source

/** Main object for the scala master
  *
  * @author Simon Koch
  *
  */
object Scama extends LogSupport {

  def parser: Parser =
    Parser(
      "scama",
      "scala master - a tool to schedule large runs of cpg static analysis programs on a batch of php projects")
      .addPositional("config", "the path to the configuration file")
      .addSubparser(
        Parser("run", "run scala master")
          .addFlag("telegram-off", "if set the telegram bot is off")
          .addOptional("worker",
                       "w",
                       "worker",
                       None,
                       "the amount of worker (overwrites config)")
          .addOptional("list",
                       "l",
                       "list",
                       None,
                       "path of allowlist of projects to use")
          .addDefault[(ParsingResult, Config) => Unit]("func", run))
      .addSubparser(
        Parser("summarize", "summarize the main log")
          .addPositional("log", "the log")
          .addOptional("joblogs",
                       "j",
                       "job-logs",
                       None,
                       "where to store failed job logs")
          .addDefault[(ParsingResult, Config) => Unit]("func", evaluateLog))
      .addSubparser(Parser("ping", "ping telegram")
        .addDefault[(ParsingResult, Config) => Unit]("func", telegramPing))

  /** the main of the scala master
    *
    * @param args the provided command line arguments
    */
  def main(args: Array[String]): Unit = {
    try {
      val pargs = parser.parse(args)
      val conf =
        ConfigFactory.parseFile(new File(pargs.getValue[String]("config")))
      pargs.getValue[(ParsingResult, Config) => Unit]("func")(pargs, conf)
    } catch {
      case _: ParsingException =>
    }
  }

  /** main to evaluate a given scala-master logfile
    *
    * @param args the parsed provided command line arguments
    * @param conf the parsed configuration file
    */
  def evaluateLog(args: ParsingResult, @nowarn conf: Config): Unit = {
    new LogEvaluator(args.getValue[String]("log"))
      .run()
      .getJobSummary(args.get[OptionalValue[String]]("joblogs").value)
  }

  /** main to run a consumer against a set of php projects
    *
    * @param pargs the parsed provided command line arguments
    * @param conf the parsed configuration
    */
  def run(pargs: ParsingResult, conf: Config): Unit = {
    val logFile = conf.getString("scama.logging.file")
    val resourceLoggin = Option(
      conf.getString("scama.logging.resourceLogging.file")) match {
      case Some(value) =>
        Some((value, conf.getLong("scama.logging.resourceLogging.interval")))
      case None => None
    }
    val telegram = if (pargs.getValue[Boolean]("telegram-off")) {
      Option(conf.getString("scama.logging.telegram"))
    } else {
      None
    }
    ScamaLogger.start(logFile, resourceLoggin, telegram)
    try {
      val allowList: List[String] =
        pargs.get[OptionalValue[String]]("list").value match {
          case Some(path: String) =>
            val bufferedFile = Source.fromFile(path)
            try {
              bufferedFile.getLines().toList
            } finally {
              bufferedFile.close()
            }
          case None => List[String]()
        }
      ScamaLogger.log(
        InformationMessage(s"Allow list: ${allowList.size} entries loaded"))
      val jobs =
        ThreadedWorkManager.createJobs(allowList.toSet)(conf)
      ScamaLogger.log(InformationMessage(s"we have ${jobs.length} jobs"))
      val worker = pargs
        .getValueOrElse[String]("worker", conf.getInt("scama.worker").toString)
        .toInt
      ScamaLogger.log(InformationMessage(s"starting $worker worker"))
      new ThreadedWorkManager(worker).startWork(jobs)
      ScamaLogger.log(InformationMessage(s"all tasks done"))
    } catch {
      case x: Throwable =>
        ScamaLogger.log(
          InformationMessage(s"unrecoverable error: ${x.getMessage}"))
        x.printStackTrace()
    } finally {
      ScamaLogger.stop()
    }
  }

  /** main to test the current telegram notification
    *
    * sends multiple messages to the configured telegram endpoint
    *
    * @param pargs the parsed provided command line arguments
    * @param config the parsed configuration
    */
  def telegramPing(@nowarn pargs: ParsingResult, config: Config): Unit = {
    val logFile = config.getString("scama.logging.file")
    val resourceLoggin = Option(
      config.getString("scama.logging.resourceLogging.file")) match {
      case Some(value) =>
        Some((value, config.getLong("scama.logging.resourceLogging.interval")))
      case None => None
    }
    val telegram = Option(config.getString("scama.logging.telegram"))
    ScamaLogger.start(logFile, resourceLoggin, telegram)
    List(
      "This is=the first test-message",
      "This is the second test.message",
      """with
        |a newline
        |""".stripMargin,
      "This is the final test=>message"
    ).foreach { msg =>
      ScamaLogger.log(InformationMessage(msg))
    }
    ScamaLogger.stop()
  }

}
