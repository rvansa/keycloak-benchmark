package org.jboss.perf

/**
  * // TODO: Document this
  *
  * @author Radim Vansa &lt;rvansa@redhat.com&gt;
  */
object Options {
  // my id and number of driver machines
  val driver = Integer.getInteger("test.driver", 0)
  val drivers = Integer.getInteger("test.drivers", 1)

  val host = System.getProperty("test.host", "keycloak");
  val port = Integer.getInteger("test.port", 8080);

  val rampUp = Integer.getInteger("test.rampUp", 2).toInt
  val rps = Integer.getInteger("test.rps", 100).toDouble
  val duration = Integer.getInteger("test.duration", 2)

  var activeUsers = Integer.getInteger("test.activeUsers", 50).toInt
  var totalUsers = Integer.getInteger("test.totalUsers", 100).toInt
  var usernameLength = Integer.getInteger("test.usernameLength", 16).toInt
  var passwordLength = Integer.getInteger("test.passwordLength", 16).toInt

  // user behaviour
  val loginFailureProbability = System.getProperty("test.loginFailureProbability", "0.2").toDouble
  val refreshTokenProbability = System.getProperty("test.refreshTokenProbability", "0.5").toDouble
  val logoutProbability = System.getProperty("test.logoutProbability", "0.8").toDouble
  val userResponsePeriod = Integer.getInteger("test.userResponsePeriod", 1).toInt
  val refreshTokenPeriod = Integer.getInteger("test.refreshTokenPeriod", 3).toInt

  // admin behaviour
  val addRemoveUserProbability = System.getProperty("test.addRemoveUserProbability", "0.2").toDouble
  val listUsersProbability = 1 - 2 * addRemoveUserProbability
}
