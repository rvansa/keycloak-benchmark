package org.jboss.perf


import io.gatling.core.Predef._
import io.gatling.core.pause.Normal
import io.gatling.core.session._
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef._
import org.keycloak.adapters.spi.HttpFacade.Cookie


import io.gatling.keycloak.Predef._
import org.keycloak.admin.client.resource.RealmResource

import scala.concurrent.forkjoin.ThreadLocalRandom

/**
  * @author Radim Vansa &lt;rvansa@redhat.com&gt;
  */
class KeycloakSimulation extends Simulation {

  val CLIENT: String = Loader.client.getClientId
  val APP_URL: String = Loader.client.getRedirectUris.stream.findFirst().get()
  val BASE_URL: String = "/auth/realms/" + Loader.realmName
  val GET_LOGIN_URL: String = BASE_URL + "/protocol/openid-connect/auth"
  val LOGOUT_URL: String = BASE_URL + "/protocol/openid-connect/logout"

  def protocolConf() = {
    http.doNotTrackHeader("1").acceptHeader("text/html").disableFollowRedirect
//        .shareConnections
  }

  def users = scenario("users")
      .exec(s => {
        val u = Feeders.borrowUser()
        if (u == null) {
          s.markAsFailed
        } else {
          s.setAll(u()).copy(userEnd = (s => {
            Feeders.returnUser(u)
          })).set("keycloak-server", randomHost())
        }
      })
      .exitHereIfFailed
      .exec(http("user.get-login").get("http://${keycloak-server}" + GET_LOGIN_URL)
        .queryParam("login", "true").queryParam("response_type", "code").queryParam("client_id", CLIENT)
        .queryParam("state", "${state}").queryParam("redirect_uri", APP_URL)
        .check(status.is(200), regex("action=\"([^\"]*)\"").saveAs("post-login-uri")))
      .exitHereIfFailed
      .pause(Options.userResponsePeriod, Normal(Options.userResponsePeriod / 10d))
      // Unsuccessful login attempts
      .asLongAs(_ => ThreadLocalRandom.current.nextDouble() < Options.loginFailureProbability) {
        exec(http("user.post-login-wrong").post("${post-login-uri}")
          .formParam("username", "${username}")
          .formParam("password", _ => Util.randomString(Options.passwordLength)).formParam("login", "Log in")
          .check(status.is(200)))
        .exitHereIfFailed
        .pause(Options.userResponsePeriod, Normal(Options.userResponsePeriod / 10d))
      }
      // Successful login
      .exec(http("user.post-login").post("${post-login-uri}")
        .formParam("username", "${username}").formParam("password", "${password}").formParam("login", "Log in")
        .check(status.is(302), header("Location").saveAs("login-redirect")))
      .exitHereIfFailed
      // Application authorizes against Keycloak
      .exec(oauth("user.authorize").authorize("${login-redirect}",
          session => List(new Cookie("OAuth_Token_Request_State", session("state").as[String], 0, null, null)))
        .authServerUrl("http://${keycloak-server}/auth").resource(CLIENT)
        .clientCredentials(Loader.client.getSecret).realm(Loader.realmName)
        .realmKey(Loader.realmRepresentation.getPublicKey))
      // Application requests to refresh the token
      .asLongAs(_ => ThreadLocalRandom.current.nextDouble() < Options.refreshTokenProbability) {
        pause(Options.refreshTokenPeriod, Normal(Options.refreshTokenPeriod / 10d))
        .exec(oauth("user.refresh-token").refresh())
      }
      // Logout or forget & let the SSO expire
      .doIf(_ => ThreadLocalRandom.current.nextDouble() < Options.logoutProbability) {
        pause(Options.refreshTokenPeriod, Normal(Options.refreshTokenPeriod / 10d))
        .exec(http("user.logout").get("http://" + randomHost() + LOGOUT_URL).queryParam("redirect_uri", APP_URL)
          .check(status.is(302), header("Location").is(APP_URL)))
      }

  def admins(scenarioName: String) = scenario(scenarioName)
    .exec(s => {
      val connection = Loader.connection(randomHost())
      s.copy(userEnd = _ => connection.close)
        .set("keycloak", connection)
        .set("realm", connection.realm(Loader.realmName))
    })

  def adminsAdd = admins("admins-add")
    .exec(s => s.set("new-user", Util.randomString(Options.usernameLength)).set("new-password", Util.randomString(Options.passwordLength)))
    .exec(admin("admin.add-user").addUser(realm, "${new-user}").firstName("Jack").lastName("Active").password("${new-password}", false).saveAs("new-id"))
    .exec(s => {
      Feeders.addUser(s("new-user").as[String], s("new-password").as[String], s("new-id").as[String])
      s
    })

  def adminsRemove = admins("admins-remove")
    .exec(s => {
      val u = Feeders.removeUser()
      if (u == null) {
        s.markAsFailed
      } else {
        s.set("removed-user", u)
      }
    })
    .exitHereIfFailed
    .doIf(user("removed-user").map(u => u.id == null)) {
      exec(admin("admin.find-user-id").findUser(realm).username(user("removed-user").username).use((s, list) => {
        user("removed-user").id(s)(list.head.getId)
        s
      }))
    }
    .exec(admin("admin.remove-user").removeUser(realm, user("removed-user").id))

  def adminsList = admins("admins-list")
    .exec(admin("admin.find-users").findUser(realm)
      .username(_ => Util.randomString(2) + "%" )
      .use((s, list) => s)
    )

  private def user(variable: String) = new UserExpression(variable)

  def realm: Expression[RealmResource] = {
    s => s("realm").as[RealmResource]
  }

  def randomHost(): String = {
    return Options.servers(ThreadLocalRandom.current().nextInt(Options.servers.length));
  }

  def run(scenario: ScenarioBuilder, opsPerSecond: Double = Options.usersPerSecond) = scenario.inject(
    rampUsersPerSec(opsPerSecond / 10d) to opsPerSecond during Options.rampUp,
    constantUsersPerSec(opsPerSecond) during Options.duration
  ).protocols(protocolConf())

  setUp(
    run(users, Options.usersPerSecond),
    run(adminsAdd, Options.adminsPerSecond * Options.addRemoveUserProbability),
    run(adminsRemove, Options.adminsPerSecond * Options.addRemoveUserProbability),
    run(adminsList, Options.adminsPerSecond * Options.listUsersProbability)
  ).maxDuration(Options.rampUp + Options.duration + Options.rampDown)
}
