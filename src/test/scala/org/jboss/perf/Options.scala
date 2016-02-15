package org.jboss.perf

/**
  * // TODO: Document this
  *
  * @author Radim Vansa &lt;rvansa@redhat.com&gt;
  */
object Options {
  // my id and number of driver machines
  val driver: Int = Integer.getInteger("test.driver", 0)
  val drivers: Int = Integer.getInteger("test.drivers", 1)

  val hosts = System.getProperty("test.hosts", "keycloak:8080").split(",").filter(p => p != null && !p.trim().isEmpty)

  val rampUp: Int = Integer.getInteger("test.rampUp", 30).toInt
  val duration: Int = Integer.getInteger("test.duration", 30).toInt
  val rampDown: Int = Integer.getInteger("test.rampDown", 30).toInt
  val usersPerSecond: Double = System.getProperty("test.usersPerSecond", "100").toDouble
  val adminsPerSecond: Double = System.getProperty("test.adminsPerSecond", "2").toDouble

  var activeUsers: Int = Integer.getInteger("test.activeUsers", 50).toInt
  var totalUsers: Int = Integer.getInteger("test.totalUsers", 100).toInt
  var usernameLength: Int = Integer.getInteger("test.usernameLength", 16).toInt
  var passwordLength: Int = Integer.getInteger("test.passwordLength", 16).toInt

  // user behaviour
  val loginFailureProbability: Double = System.getProperty("test.loginFailureProbability", "0.2").toDouble
  val refreshTokenProbability: Double = System.getProperty("test.refreshTokenProbability", "0.5").toDouble
  val logoutProbability: Double = System.getProperty("test.logoutProbability", "0.8").toDouble
  val userResponsePeriod: Int = Integer.getInteger("test.userResponsePeriod", 1).toInt
  val refreshTokenPeriod: Int = Integer.getInteger("test.refreshTokenPeriod", 3).toInt

  // admin behaviour
  val addRemoveUserProbability: Double = System.getProperty("test.addRemoveUserProbability", "0.2").toDouble
  val listUsersProbability: Double = 1 - 2 * addRemoveUserProbability
}
