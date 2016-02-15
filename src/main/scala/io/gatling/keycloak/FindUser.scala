package io.gatling.keycloak

import akka.actor.ActorRef
import akka.actor.ActorDSL._
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.action.Interruptable
import io.gatling.core.config.Protocols
import io.gatling.core.result.writer.DataWriterClient
import io.gatling.core.session.{Expression, Session}
import io.gatling.core.validation.Validation
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.UserRepresentation

import scala.collection.JavaConversions._

case class FindUserAttributes(
  requestName: Expression[String],
  realm: Expression[RealmResource],
  username: Option[Expression[String]] = None,
  firstName: Option[Expression[String]] = None,
  lastName: Option[Expression[String]] = None,
  email: Option[Expression[String]] = None,
  firstResult: Option[Expression[Integer]] = None,
  maxResults: Option[Expression[Integer]] = None,
  use: Option[(Session, List[UserRepresentation]) => Session] = None
) {}

object FindUserBuilder {
  implicit def toActionBuilder(builder: FindUserBuilder) = new FindUserActionBuilder(builder.attributes)
}

class FindUserBuilder(private val attributes: FindUserAttributes) {
  def username(username: Expression[String]) = new FindUserBuilder(attributes.copy(username = Some(username)))
  def firstName(firstName: Expression[String]) = new FindUserBuilder(attributes.copy(firstName = Some(firstName)))
  def lastName(lastName: Expression[String]) = new FindUserBuilder(attributes.copy(lastName = Some(lastName)))
  def email(email: Expression[String]) = new FindUserBuilder(attributes.copy(email = Some(email)))
  def firstResult(firstResult: Expression[Integer]) = new FindUserBuilder(attributes.copy(firstResult = Some(firstResult)))
  def maxResults(maxResults: Expression[Integer]) = new FindUserBuilder(attributes.copy(maxResults = Some(maxResults)))
  def use(use: (Session, List[UserRepresentation]) => Session) = new FindUserActionBuilder(attributes.copy(use = Some(use)))
}

class FindUserActionBuilder(attributes: FindUserAttributes) extends ActionBuilder {
  override def build(next: ActorRef, protocols: Protocols): ActorRef =
    actor(actorName("find-user"))(new FindUserAction(attributes, next))
}

class FindUserAction(
                      attributes: FindUserAttributes,
                      val next: ActorRef
                    ) extends Interruptable with ExitOnFailure with DataWriterClient {
  override def executeOrFail(session: Session): Validation[_] =
    attributes.realm(session).flatMap(realm =>
      Blocking(() =>
        Stopwatch(() => {
          realm.users().search(
            attributes.username.map(a => a(session).get).orNull,
            attributes.firstName.map(a => a(session).get).orNull,
            attributes.lastName.map(a => a(session).get).orNull,
            attributes.email.map(a => a(session).get).orNull,
            attributes.firstResult.map(a => a(session).get).orNull,
            attributes.maxResults.map(a => a(session).get).orNull)
        })
          .recordAndContinue(this, session, attributes.requestName(session).get, users => {
            attributes.use.map(use => use(session, users.toList)).getOrElse(session)
          })
      )
    )
}
