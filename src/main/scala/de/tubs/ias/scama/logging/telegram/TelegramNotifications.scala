package de.tubs.ias.scama.logging.telegram

import de.tubs.ias.scama.logging.{InformationMessage, ScamaLogger}
import scalaj.http.{Http, HttpOptions}
import wvlet.log.LogSupport

import java.net.URLEncoder
import scala.collection.mutable

//https://api.telegram.org/bot**YourBOTToken**/getUpdates

object TelegramNotifications extends LogSupport {

  private val messageDeque: mutable.ArrayDeque[String] = mutable.ArrayDeque()
  private var thread: Option[Thread] = None
  private var botApiKey: String = ""
  private var chatId: String = ""
  private val connectionTimeout: Int = 1000 * 10
  private val readTimeout: Int = 1000 * 10

  def startTelegramBot(apiKey: String, givenChatId: String): Unit = {
    botApiKey = apiKey
    chatId = givenChatId
    createMessageThread()
  }

  def finishUp(): Unit = {
    thread match {
      case Some(_) =>
        messageDeque.synchronized {
          val buff = thread.get
          thread = None
          messageDeque.notify()
          messageDeque.wait()
          buff.join()
        }
      case None =>
    }
  }

  //todo: check for bad interleaving when finishing with(out) any messages
  private def createMessageThread(): Unit = {
    if (thread.isEmpty) {
      this.thread = Some(new Thread {
        override def run(): Unit = {
          var running: Boolean = true
          while (running) {
            messageDeque.synchronized {
              running = thread.nonEmpty || messageDeque.nonEmpty
              messageDeque.removeHeadOption() match {
                case Some(message) =>
                  sendTelegramMessage(message)
                case None if running =>
                  messageDeque.wait()
                case _ =>
              }
            }
          }
          messageDeque.synchronized {
            messageDeque.notify()
          }
        }
      })
      thread.get.start()
      ScamaLogger.log(InformationMessage("telegram bot started"))
    }
  }

  //I hate this but java is beyond stupid
  private def encodeId(message: String): String = {
    URLEncoder
      .encode(message, "UTF-8")
      .replace(".", "\\%2E")
      .replace("-", "%2D")
  }

  private def encodeMessage(message: String): String = {
    val legalChar = Set(
      ".",
      "-",
      "=",
      ">",
      "(",
      ")",
      "/",
      "\n",
      " ",
      "[",
      "]",
      "a",
      "b",
      "c",
      "d",
      "e",
      "f",
      "g",
      "h",
      "i",
      "j",
      "k",
      "l",
      "m",
      "n",
      "o",
      "p",
      "q",
      "r",
      "s",
      "t",
      "u",
      "v",
      "w",
      "x",
      "y",
      "z",
      "1",
      "2",
      "3",
      "4",
      "5",
      "6",
      "7",
      "8",
      "9",
      "0"
    )
    val filtered = message.toLowerCase.filter { character =>
      legalChar.contains(character.toString)
    }
    filtered
      .replace(".", "\\%2E")
      .replace("-", "\\%2D")
      .replace("=", "\\%3D")
      .replace(">", "\\%3E")
      .replace("(", "\\%28")
      .replace(")", "\\%29")
      .replace("/", "%2F")
      .replace("\n", "%0A")
      .replace(" ", "+")
  }

  def splitString(message: String, size: Int): Seq[String] = {
    message.toCharArray.toList
      .grouped(size)
      .map { seq =>
        seq.mkString
      }
      .toList
  }

  private def sendTelegramMessage(message: String): Unit = {
    if (message.length >= 4096) {
      splitString(message, 4000).foreach(sendMessage)
    } else {
      var daurl: Option[String] = None
      try {
        //todo: url encoder does not do proper url encoding for url
        val encodedChatId = encodeId(chatId)
        val encodedMessage = encodeMessage(message)
        val url = {
          s"https://api.telegram.org/bot$botApiKey/sendMessage?chat_id=$encodedChatId&parse_mode=MarkdownV2&text=$encodedMessage"
        }
        daurl = Some(url)
        val response = Http(url)
          .options(HttpOptions.followRedirects(true))
          .timeout(connectionTimeout, readTimeout)
          .asString
        if (response.code != 200) {
          error(
            s"Telgram Bot encountered a non 200 response code for $url\n${response.body}\n")
        }
      } catch {
        case x: Throwable =>
          error(
            s"Telegram encountered error when sending message:\n${x.getMessage}//$daurl")
      }
    }
  }

  def sendMessage(message: String): Unit = {
    messageDeque.synchronized {
      thread match {
        case Some(_) =>
          messageDeque.append(message)
          messageDeque.notify()
        case None =>
          debug(s"added telegram message $message but bot is not active")
      }
    }
  }

}
