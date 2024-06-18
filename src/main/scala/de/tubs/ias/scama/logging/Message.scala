package de.tubs.ias.scama.logging

import wvlet.log.LogLevel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** trait that represents a message that can be logged
  *
  * @author Simon Koch
  *
  */
trait Message {

  /** the time the message was created
    */
  private val created: String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd/HH:mm:ss").format(LocalDateTime.now)

  /** whether or not this message should be logged via telegram
    */
  protected var telegram = true

  def getTelegram: Boolean = telegram

  /** the log level of the message
    */
  protected val logLevel: LogLevel = LogLevel.INFO

  def getLogLevel: LogLevel = logLevel

  /** get the string representing the message
    *
    * @return the stringyfied message
    */
  def getLogMessage: String = {
    s"[$created]$getLogMessageContent"
  }

  /** set telegram usage to false
    *
    * @return the message with telegram deactivated
    */
  def noTelegram: Message = {
    this.telegram = false
    this
  }

  /** function to generate the content of the message
    *
    * this has to be implemented by all implementing classes
    *
    * @return the content of the message to be logged
    */
  protected def getLogMessageContent: String
}

trait InfoMessage extends Message {
  override val logLevel: LogLevel = LogLevel.INFO
}

trait ErrMessage extends Message {
  override val logLevel: LogLevel = LogLevel.ERROR
}

trait DebugMessage extends Message {
  override val logLevel: LogLevel = LogLevel.DEBUG
}

trait WarnMessage extends Message {
  override val logLevel: LogLevel = LogLevel.WARN
}

case class StartMessage() extends InfoMessage {

  protected override def getLogMessageContent: String = "started logging"

}

case class StopMessage() extends InfoMessage {

  protected override def getLogMessageContent: String = "stopped logging"

}

case class InformationMessage(info: String) extends InfoMessage {
  protected override def getLogMessageContent: String = info
}

case class ErrorMessage(error: String) extends ErrMessage {
  protected override def getLogMessageContent: String = error
}
