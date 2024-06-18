package de.tubs.ias.scama.logging

import wvlet.log.LogLevel

trait JobMessage extends Message {

  val project: String

  protected override def getLogMessageContent: String =
    s"[$project]$getJobLogMessageContent"

  protected def getJobLogMessageContent: String

}

case class JobStartMessage(override val project: String) extends JobMessage {
  override protected def getJobLogMessageContent: String = s" started"
}

case class JobInfoMessage(override val project: String, info: String)
    extends JobMessage {
  override protected def getJobLogMessageContent: String = info
}

case class JobSuccessfulEndMessage(override val project: String)
    extends JobMessage {
  override protected def getJobLogMessageContent: String = " finished"
}

case class JobFailureMessage(override val project: String, reason: String)
    extends JobMessage {
  override val logLevel: LogLevel = LogLevel.WARN
  override protected def getJobLogMessageContent: String =
    s"failed due to $reason"
}

trait JobCpgMessage extends JobMessage {
  protected override def getLogMessageContent: String =
    s"[$project][cpg]$getJobLogMessageContent"
}

trait JobConsumerMessage extends JobMessage {
  protected override def getLogMessageContent: String =
    s"[$project][consumer]$getJobLogMessageContent"
}

case class JobCpgCreationStartMessage(override val project: String)
    extends JobCpgMessage {
  override protected def getJobLogMessageContent: String = s"started"
}

case class JobCpgCreationSuccessMessage(override val project: String)
    extends JobCpgMessage {
  override protected def getJobLogMessageContent: String = s"finished"
}

case class JobCpgCreationFailureMessage(override val project: String,
                                        reason: String)
    extends JobCpgMessage {
  override val logLevel: LogLevel = LogLevel.WARN
  override protected def getJobLogMessageContent: String =
    s"failed due to $reason"
}

case class JobConsumerStartMessage(override val project: String)
    extends JobConsumerMessage {
  override protected def getJobLogMessageContent: String = s"started"
}

case class JobConsumerSuccessMessage(override val project: String)
    extends JobConsumerMessage {
  override protected def getJobLogMessageContent: String = s"finished"
}

case class JobConsumerFailureMessage(override val project: String,
                                     reason: String)
    extends JobConsumerMessage {
  override val logLevel: LogLevel = LogLevel.WARN
  override protected def getJobLogMessageContent: String =
    s"failed due to $reason"
}
