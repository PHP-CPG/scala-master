package de.tubs.ias.scama.job

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.{Await, Future, TimeoutException, blocking, duration}
import scala.concurrent.ExecutionContext.Implicits.global
import sys.process._

class ThreadedWorkTest extends AnyWordSpec with Matchers {

  "ensure that the thread terminates in time" should {
    "work with future and Process" in {
      val process = "sleep 100".run()
      val future = Future(blocking{
        process.exitValue()
      })
      assertThrows[TimeoutException](Await.result(future,duration.Duration(2, "sec")))
      process.destroy()
    }
  }

  /*"snitching" should {
    "not throw an error" in {
      println(ThreadedWorkManager.snitch())
    }
  }*/

}
