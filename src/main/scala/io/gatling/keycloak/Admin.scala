package io.gatling.keycloak

import akka.actor.ActorDSL._
import akka.actor.ActorRef
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.config.Protocols
import io.gatling.core.session.Expression
import org.keycloak.admin.client.resource.RealmResource

/**
  * @author Radim Vansa &lt;rvansa@redhat.com&gt;
  */
case class Admin(requestName: Expression[String]) {
  def addUser(realm: Expression[RealmResource], username: Expression[String]) =
    new AddUserActionBuilder(new AddUserAttributes(requestName, realm, username))

  def removeUser(realm: Expression[RealmResource], userId: Expression[String]) =
    new ActionBuilder {
      override def build(next: ActorRef, protocols: Protocols): ActorRef =
        actor(actorName("remove-user"))(new RemoveUserAction(requestName, realm, userId, next))
    }

  def findUser(realm: Expression[RealmResource]) =
    new FindUserBuilder(new FindUserAttributes(requestName, realm))

}
