package io.gatling.keycloak

import akka.actor.ActorDSL._
import akka.actor.ActorRef
import io.gatling.core.action.{Failable, Interruptable}
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.config.Protocols
import io.gatling.core.result.message.{KO, OK}
import io.gatling.core.result.writer.DataWriterClient
import io.gatling.core.session.{Session, Expression}
import io.gatling.core.util.TimeHelper
import io.gatling.core.validation.{Failure, Success, Validation}

/**
  * @author Radim Vansa &lt;rvansa@redhat.com&gt;
  */
class RefreshTokenActionBuilder(requestName: Expression[String]) extends ActionBuilder{
  override def build(next: ActorRef, protocols: Protocols): ActorRef = {
    actor(actorName("refresh-token"))(new RefreshTokenAction(requestName, next))
  }
}

class RefreshTokenAction(
                          requestName: Expression[String],
                          val next: ActorRef
                        ) extends Interruptable with ExitOnFailure with DataWriterClient {
  override def executeOrFail(session: Session): Validation[_] = {
    val requestAuth: MockRequestAuthenticator = session(MockRequestAuthenticator.KEY).as[MockRequestAuthenticator]

    Stopwatch(() => requestAuth.getKeycloakSecurityContext.refreshExpiredToken(true))
      .check(identity, _ => "Could not refresh token")
      .record(this, session, requestName(session).get)
      .map(_ => next ! session)
  }
}
