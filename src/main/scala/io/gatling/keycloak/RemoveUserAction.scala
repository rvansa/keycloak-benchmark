package io.gatling.keycloak

import akka.actor.ActorRef
import io.gatling.core.action.{Failable, Interruptable}
import io.gatling.core.result.writer.DataWriterClient
import io.gatling.core.session.{Expression, Session}
import io.gatling.core.validation.Validation
import org.keycloak.admin.client.resource.RealmResource

/**
  * @author Radim Vansa &lt;rvansa@redhat.com&gt;
  */
class RemoveUserAction(
                        requestName: Expression[String],
                        realm: Expression[RealmResource],
                        userId: Expression[String],
                        val next: ActorRef
                      ) extends Interruptable with ExitOnFailure with DataWriterClient {
  override def executeOrFail(session: Session): Validation[_] = {
    realm(session).map(realm =>
      Stopwatch(() => realm.users.get(userId(session).get).remove())
        .record(this, session, requestName(session).get)
        .map(_ => next ! session)
    )
  }
}
