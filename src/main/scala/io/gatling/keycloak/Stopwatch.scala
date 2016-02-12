package io.gatling.keycloak

import io.gatling.core.result.message.{OK, KO, Status}
import io.gatling.core.result.writer.DataWriterClient
import io.gatling.core.session.Session
import io.gatling.core.util.TimeHelper
import io.gatling.core.validation.{Validation, Success, Failure}

/**
  * @author Radim Vansa &lt;rvansa@redhat.com&gt;
  */
object Stopwatch {
  def apply[T](f: () => T): Result[T] = {
    val start = TimeHelper.nowMillis
    try {
      val result = f()
      Result(Success(result), OK, start, TimeHelper.nowMillis)
    } catch {
      case e: Throwable => {
        Result(Failure(e.toString), KO, start, TimeHelper.nowMillis)
      }
    }
  }
}

case class Result[T](
                      val value: Validation[T],
                      val status: Status,
                      val startTime: Long,
                      val endTime: Long
) {
  def check(check: T => Boolean, fail: T => String): Result[T] = {
     value match {
       case Success(v) =>
         if (!check(v)) {
           System.err.println("Request failed on : " + v)
           Result(Failure(fail(v)), KO, startTime, endTime);
         } else {
           this
         }
       case _ => this
     }
  }

  def isSuccess =
    value match {
      case Success(_) => true
      case _ => false
    }

  def record(dataWriterClient: DataWriterClient, session: Session, name: String): Validation[T] = {
    dataWriterClient.writeRequestData(session, name, startTime, startTime, endTime, endTime, status)
    value
  }
}

