package io.gatling.keycloak

import java.util.Collections

import akka.actor.ActorDSL.actor
import akka.actor.ActorRef
import io.gatling.core.action.{UserEnd, Failable, Interruptable}
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.config.Protocols
import io.gatling.core.result.writer.DataWriterClient
import io.gatling.core.session._
import io.gatling.core.validation._
import org.keycloak.adapters.spi.AuthOutcome
import org.keycloak.adapters.{KeycloakDeployment, KeycloakDeploymentBuilder}
import org.keycloak.adapters.spi.HttpFacade.Cookie
import org.keycloak.common.enums.SslRequired
import org.keycloak.representations.adapters.config.AdapterConfig

import scala.collection.JavaConverters._

case class AuthorizeAttributes(
  requestName: Expression[String],
  uri: Expression[String],
  cookies: Expression[List[Cookie]],
  sslRequired: SslRequired = SslRequired.EXTERNAL,
  resource: String = null,
  password: String = null,
  realm: String = null,
  realmKey: String = null,
  authServerUrl: String = null
) {
  def toAdapterConfig = {
    val adapterConfig = new AdapterConfig
    adapterConfig.setSslRequired(sslRequired.toString)
    adapterConfig.setResource(resource)
    adapterConfig.setCredentials(Collections.singletonMap("secret", password))
    adapterConfig.setRealm(realm)
    adapterConfig.setRealmKey(realmKey)
    adapterConfig.setAuthServerUrl(authServerUrl)
    adapterConfig
  }
}

class AuthorizeActionBuilder(attributes: AuthorizeAttributes) extends ActionBuilder {
  def newInstance(attributes: AuthorizeAttributes) = new AuthorizeActionBuilder(attributes)

  def sslRequired(sslRequired: SslRequired) = newInstance(attributes.copy(sslRequired = sslRequired))
  def resource(resource: String) = newInstance(attributes.copy(resource = resource))
  def clientCredentials(password: String) = newInstance(attributes.copy(password = password))
  def realm(realm: String) = newInstance(attributes.copy(realm = realm))
  def realmKey(realmKey: String) = newInstance(attributes.copy(realmKey = realmKey))
  def authServerUrl(authServerUrl: String) = newInstance(attributes.copy(authServerUrl = authServerUrl))

  override def build(next: ActorRef, protocols: Protocols): ActorRef = {
    var facade = new MockHttpFacade()
    var deployment = KeycloakDeploymentBuilder.build(attributes.toAdapterConfig);
    actor(actorName("authorize"))(new AuthorizeAction(deployment, facade, attributes.requestName, attributes.uri, attributes.cookies, next))
  }
}

class AuthorizeAction(
                       deployment: KeycloakDeployment,
                       facade: MockHttpFacade,
                       requestName: Expression[String],
                       uri: Expression[String],
                       cookies: Expression[List[Cookie]],
                       val next: ActorRef
                     ) extends Interruptable with ExitOnFailure with DataWriterClient {
  override def executeOrFail(session: Session): Validation[_] = {
    facade.request.setURI(uri(session).get);
    facade.request.setCookies(cookies(session).get.map(c => (c.getName, c)).toMap.asJava)
    var nextSession = session
    val requestAuth: MockRequestAuthenticator = session(MockRequestAuthenticator.KEY).asOption[MockRequestAuthenticator] match {
      case Some(ra) => ra
      case None =>
        val tmp = new MockRequestAuthenticator(facade, deployment, new MockTokenStore, -1)
        nextSession = session.set(MockRequestAuthenticator.KEY, tmp)
        tmp
    }

    Stopwatch(() => requestAuth.authenticate())
      .check(result => result == AuthOutcome.AUTHENTICATED, result => result.toString)
      .record(this, nextSession, requestName(session).get)
      .map(_ => next ! nextSession)
  }
}

