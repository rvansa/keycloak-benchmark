package org.jboss.perf


import io.gatling.core.Predef
import io.gatling.core.Predef._
import io.gatling.core.pause.Normal
import io.gatling.core.session._
import io.gatling.core.validation.{Failure, Validation, Success}
import io.gatling.http.Predef._
import io.gatling.http.request.builder.{HttpRequestBuilder, Http}
import org.jboss.perf.model.User
import org.keycloak.adapters.spi.HttpFacade.Cookie


import io.gatling.keycloak.Predef._
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.RealmResource

import scala.concurrent.forkjoin.ThreadLocalRandom

/**
  * @author Radim Vansa &lt;rvansa@redhat.com&gt;
  */
class KeycloakSimulation extends Simulation {

  val CLIENT: String = Loader.client.getClientId
  val APP_URL: String = Loader.client.getRedirectUris.stream.findFirst().get()
  val OAUTH_URL: String = "http://" + Options.host + ":" + Options.port + "/auth"
  val BASE_URL: String = "/auth/realms/" + Loader.realmName
  val GET_LOGIN_URL: String = BASE_URL + "/protocol/openid-connect/auth"
  val LOGOUT_URL: String = BASE_URL + "/protocol/openid-connect/logout"

  @volatile
  var blackhole: Any = null;

  def protocolConf() = {
    http.baseURL("http://" + Options.host + ":" + Options.port).doNotTrackHeader("1").shareConnections.acceptHeader("text/html").disableFollowRedirect
  }

  def users = scenario("users")
      .feed(Feeders.activeUsersFeeder)
      .exec(http("user.get-login").get(GET_LOGIN_URL)
        .queryParam("login", "true").queryParam("response_type", "code").queryParam("client_id", CLIENT)
        .queryParam("state", "${state}").queryParam("redirect_uri", APP_URL)
        .check(regex("action=\"([^\"]*)\"").saveAs("post-login-uri")))
      .pause(Options.userResponsePeriod, Normal(Options.userResponsePeriod / 10d))
      // Unsuccessful login attempts
      .asLongAs(_ => ThreadLocalRandom.current.nextDouble() < Options.loginFailureProbability) {
        exec(http("user.post-login-wrong").post("${post-login-uri}")
          .formParam("username", "${username}")
          .formParam("password", _ => Util.randomString(Options.passwordLength)).formParam("login", "Log in")
          .check(status.is(200)))
        .pause(Options.userResponsePeriod, Normal(Options.userResponsePeriod / 10d))
      }
      // Successful login
      .exec(http("user.post-login").post("${post-login-uri}")
        .formParam("username", "${username}").formParam("password", "${password}").formParam("login", "Log in")
        .check(status.is(302), header("Location").saveAs("login-redirect")))
      .exitHereIfFailed
      // Application authorizes against Keycloak
      .exec(oauth("user.authorize").authorize("${login-redirect}", session => List(new Cookie("OAuth_Token_Request_State", session("state").as[String], 0, null, null)))
        .authServerUrl(OAUTH_URL).resource(CLIENT).clientCredentials(Loader.client.getSecret).realm(Loader.realmName).realmKey(Loader.realmRepresentation.getPublicKey))
      // Application requests to refresh the token
      .asLongAs(_ => ThreadLocalRandom.current.nextDouble() < Options.refreshTokenProbability) {
        pause(Options.refreshTokenPeriod, Normal(Options.refreshTokenPeriod / 10d))
        .exec(oauth("user.refresh-token").refresh())
      }
      // Logout or forget & let the SSO expire
      .doIf(_ => ThreadLocalRandom.current.nextDouble() < Options.logoutProbability) {
        pause(Options.refreshTokenPeriod, Normal(Options.refreshTokenPeriod / 10d))
        .exec(http("user.logout").get(LOGOUT_URL).queryParam("redirect_uri", APP_URL)
          .check(status.is(302), header("Location").is(APP_URL)))
      }

  def admins = scenario("admins")
    .exec(s => s.set("keycloak", Loader.connection))
    .exec(s => s.set("realm", s("keycloak").as[Keycloak].realm(Loader.realmName)))
    .randomSwitch(
      (Options.addRemoveUserProbability * 100,
        exec(s => s.set("new-user", Util.randomString(Options.usernameLength)).set("new-password", Util.randomString(Options.passwordLength)))
        .exec(admin("admin.add-user").addUser(realm, "${new-user}").password("${new-password}", false).saveAs("new-id"))
        .exec(s => {
          Feeders.addUser(s("new-user").as[String], s("new-password").as[String], s("new-id").as[String])
          s
        })),
      (Options.addRemoveUserProbability * 100,
        exec(s => {
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
        .exec(admin("admin.remove-user").removeUser(realm, user("removed-user").id))),
      (Options.listUsersProbability * 100,
        exec(admin("admin.find-users").findUser(realm).use((s, list) => {
          list.foreach(u => blackhole = u)
          s
        })))
    )

  private def user(variable: String) = new UserExpression(variable)

  private class UserExpression(val variable: String) extends Expression[User] {
    override def apply(s: Session): Validation[User] = s(variable).validate[User]
    def id: IdExpression = new IdExpression(s => apply(s))
    def username: Expression[String] = s => apply(s).map(u => u.username)
  }

  // the below is a bit of overkill, but something like that might be useful for more complicated scenarios
  private class IdExpression(expr: Expression[User]) extends Expression[String] {
    override def apply(s: Session): Validation[String] with IdSettable = expr(s) match {
      case Success(user) => new IdSuccess(user)
      case Failure(msg) => new IdFailure(msg)
    }
  }

  private trait IdSettable {
    def apply(id: String)
  }

  private class IdSuccess(user: User) extends Success[String](user.id) with IdSettable {
    def apply(id: String) = (user.id = id)
  }

  private class IdFailure(msg: String) extends Failure(msg) with IdSettable {
    def apply(id: String) = throw new UnsupportedOperationException()
  }

  def realm: Expression[RealmResource] = {
    s => s("realm").as[RealmResource]
  }

  setUp(
    users.inject(
      rampUsersPerSec(Options.usersPerSecond / 10) to Options.usersPerSecond during Options.rampUp,
      constantUsersPerSec(Options.usersPerSecond) during Options.duration
    ).protocols(protocolConf()),
    admins.inject(
      rampUsersPerSec(Options.adminsPerSecond / 10) to Options.adminsPerSecond during Options.rampUp,
      constantUsersPerSec(Options.adminsPerSecond) during Options.duration
    ).protocols(protocolConf())
  ).maxDuration(Options.rampUp + Options.duration + Options.rampDown)
}
