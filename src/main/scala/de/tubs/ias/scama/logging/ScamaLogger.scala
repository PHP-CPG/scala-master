package de.tubs.ias.scama.logging

import com.typesafe.config.ConfigFactory
import de.tubs.ias.scama.logging.telegram.TelegramNotifications
import wvlet.log.LogFormatter.SimpleLogFormatter
import wvlet.log.{LogLevel, LogSupport}
import java.io.{BufferedWriter, File, FileWriter}
import java.util.concurrent.ConcurrentLinkedQueue

/** Logging class providing a background thread to keep writing logging messages
  *
  * @author Simon Koch
  *
  * @param file the file into which to write the main log
  * @param resourceLogging the file into which to write the resource log
  * @param telegram path to the telegram config, if not provided telegram will not be used
  */
class ScamaLogger(file: String,
                  resourceLogging: Option[(String, Long)],
                  telegram: Option[String])
    extends Thread
    with LogSupport {

  logger.setFormatter(SimpleLogFormatter)

  /** whether or not the logger is still supposed to be running
    */
  private var running: Boolean = true

  /** the used instance of the resource logger
    */
  private val resourceLogger: Option[(String, ResourceLogger)] =
    resourceLogging match {
      case Some((file, interval)) => Some((file, new ResourceLogger(interval)))
      case None                   => None
    }

  /** thread safe queue of outstanding logging messages
    */
  private val messageQueue = new ConcurrentLinkedQueue[Message]()

  /** start telegram logging bot
    *
    */
  private def startTelegram(): Unit = {
    telegram match {
      case Some(value) =>
        if (new File(value).exists()) {
          val telegramConf = ConfigFactory.parseFile(new File(value))
          val apiKey = telegramConf.getString("telegram.apiKey")
          val chatId = telegramConf.getString("telegram.chatId")
          TelegramNotifications.startTelegramBot(apiKey, chatId)
        }
      case None =>
    }
  }

  /** add the provided message to the message queue
    *
    * @param message the log message
    */
  private def log(message: Message): Unit = this.synchronized {
    messageQueue.add(message)
    this.notify()
  }

  /** add the stop message to the queue and thus stop the logging process
    *
    */
  private def stopLogger(): Unit = log(StopMessage())

  /** send a message via telegram
    *
    * @param msg the message to be send
    */
  private def handleMessageTelegram(msg: Message): Unit = {
    if (msg.getTelegram && telegram.nonEmpty)
      TelegramNotifications.sendMessage(
        msg.getLogMessage.substring(21).replace('[', ' ').replace(']', ' '))
  }

  /** write a message to all streams of interest
    *
    * @param msg the message to write
    * @param main the stream to write to
    * @param resource the stream to write to if the message is a resource usage message
    */
  private def handleMessageWrite(msg: Message,
                                 main: BufferedWriter,
                                 resource: Option[BufferedWriter]): Unit = {
    msg match {
      case rm: ResourceUsageMessage =>
        resource.get.write(rm.getLogMessage + "\n")
      case other => main.write(other.getLogMessage + "\n")
    }
  }

  /** handle writing a message to the console
    *
    * @param msg the message to write
    */
  private def handleMessageConsole(msg: Message): Unit = {
    val msgValue = msg.getLogMessage
    msg.getLogLevel match {
      case LogLevel.ERROR => error(msgValue)
      case LogLevel.WARN  => warn(msgValue)
      case LogLevel.INFO  => info(msgValue)
      case _              => debug(msgValue)
    }
  }

  /** main function to handle a message across all mediums
    *
    * @param msg the message to handle
    * @param main the writer to write the main log
    * @param resource the writer to write the resource log (if provided)
    */
  private def handleMessage(msg: Message,
                            main: BufferedWriter,
                            resource: Option[BufferedWriter]): Unit = {
    handleMessageTelegram(msg)
    handleMessageWrite(msg, main, resource)
    handleMessageConsole(msg)
  }

  /** start the background thread to keep logging
    *
    */
  override def run(): Unit = {
    val pw = new BufferedWriter(new FileWriter(new File(file)))
    var rw: Option[BufferedWriter] = None
    startTelegram()
    resourceLogger match {
      case Some((file, value)) =>
        rw = Some(new BufferedWriter(new FileWriter(new File(file))))
        value.start()
      case None =>
    }
    try {
      this.synchronized {
        while (running) {
          Option(messageQueue.poll()) match {
            case Some(value: Message) =>
              handleMessage(value, pw, rw)
              if (value.isInstanceOf[StopMessage]) running = false
            case None => this.wait()
          }
        }
      }
    } finally {
      resourceLogger match {
        case Some((_, value)) =>
          rw.get.flush()
          rw.get.close()
          value.stopResourceLogger()
        case None =>
      }
      TelegramNotifications.finishUp()
      pw.flush()
      pw.close()
    }
  }
}

/** Singleton API to interact with the scala master logger
  *
  * @author Simon Koch
  *
  */
object ScamaLogger {

  /** handler of the logger instance running
    */
  private var logger: Option[ScamaLogger] = None

  /** start the logging
    *
    * @param file the file into which to write the main log file
    * @param resourceLogging the file into which to write the resource log
    * @param telegram the path to the configuration file for telegram, if not provided no telegram messages are sent
    * @return
    */
  def start(file: String,
            resourceLogging: Option[(String, Long)],
            telegram: Option[String]): ScamaLogger = {
    logger = Some(new ScamaLogger(file, resourceLogging, telegram))
    log(StartMessage())
    this.get.start()
    this.get
  }

  /** stop the logging and clean up
    *
    */
  def stop(): Unit = {
    get.stopLogger()
  }

  /** log the provided message
    *
    * @param message the message to be logged
    */
  def log(message: Message): Unit = {
    get.log(message)
  }

  /** log an info message
    *
    * @param msg the message
    * @param telegram whether or not the message should also be send via telegram
    */
  def info(msg: String, telegram: Boolean = true): Unit = {
    if (telegram)
      log(InformationMessage(msg))
    else
      log(InformationMessage(msg).noTelegram)
  }

  /** log an error message
    *
    * @param msg the message
    * @param telegram whether or not the message should also be send via telegram
    */
  def error(msg: String, telegram: Boolean = true): Unit = {
    if (telegram)
      log(ErrorMessage(msg))
    else
      log(ErrorMessage(msg).noTelegram)
  }

  /** get the running scala master logger
    *
    * exception if no logger has been started
    *
    * @return the logger instance
    */
  def get: ScamaLogger = {
    logger match {
      case Some(value) => value
      case None =>
        throw new RuntimeException("logger needs to be started first")
    }
  }

}
