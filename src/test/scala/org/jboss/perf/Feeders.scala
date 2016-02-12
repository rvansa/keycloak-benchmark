package org.jboss.perf

import io.gatling.core.Predef._
import org.jboss.perf.model.User
import org.jboss.perf.Util._
import org.jboss.perf.util.RandomDataProvider

import scala.concurrent.forkjoin.ThreadLocalRandom
import scala.util.Random

/**
  * @author Radim Vansa &lt;rvansa@redhat.com&gt;
  */
object Feeders {

  val activeUsers = generateCredentials(Options.activeUsers)
  val totalUsers = random.shuffle(activeUsers ++ generateCredentials(Options.totalUsers - Options.activeUsers))

  def generateCredentials(count: Int): IndexedSeq[User] = {
    Range(0, count).map(_ => User(randomString(Options.usernameLength, Util.random), randomString(Options.passwordLength, Util.random), null))
  }

  val activeUsersProvider = new RandomDataProvider[Map[String, String]](activeUsers
    // this driver node will use only a subset of users
    .filter(u => u.username.hashCode % Options.drivers == Options.driver)
    .map(u => addState(u.toMap, random)))

  val activeUsersFeeder : Feeder[String] = activeUsersProvider.iterator(ThreadLocalRandom.current())

  def addUser(username : String, password: String, id : String): Unit = {
    activeUsersProvider += addState(new User(username, password, id).toMap)
  }

  def removeUser() = {
    val map: Map[String, String] = activeUsersProvider.randomRemove(ThreadLocalRandom.current())
    if (map == null) null else new User(map)
  }

  private def addState(map : Map[String, String], rand: Random = ThreadLocalRandom.current()) = {
    map.updated("state", randomUUID(rand))
  }
}
