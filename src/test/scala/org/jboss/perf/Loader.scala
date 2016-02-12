package org.jboss.perf

import java.util.function.{Predicate, Consumer}
import javax.ws.rs.core.{Response, HttpHeaders}

import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.UsersResource
import org.keycloak.common.util.{CertificateUtils, Base64}
import org.keycloak.representations.idm.{UserRepresentation, RealmRepresentation}
import org.keycloak.util.JsonSerialization

/**
  * @author Radim Vansa &lt;rvansa@redhat.com&gt;
  */
object Loader {
  // TODO: catch exceptions
  val realmRepresentation: RealmRepresentation = JsonSerialization.readValue(getClass.getResourceAsStream("/keycloak/benchmark-realm.json"), classOf[RealmRepresentation])
  val realmName = realmRepresentation.getRealm
  val client = realmRepresentation.getClients().stream().findFirst().get()


  realmRepresentation.setPublicKey(Security.PublicKey)
  realmRepresentation.setPrivateKey(Security.PrivateKey)
  realmRepresentation.setCertificate(Security.Certificate)

  def connection: Keycloak = {
    Keycloak.getInstance("http://" + Options.host + ":" + Options.port + "/auth", "master", "admin", "admin", "admin-cli")
  }

  def main(args: Array[String]) {
    val keycloak: Keycloak = connection
    var optRealm = keycloak.realms().findAll().stream().filter((r: RealmRepresentation) => r.getRealm.equals(realmRepresentation.getRealm)).findFirst()
    if (optRealm.isPresent) {
      keycloak.realm(realmRepresentation.getRealm).remove()
    }
    keycloak.realms().create(realmRepresentation)
    val realmResource = keycloak.realm(realmRepresentation.getRealm)
    val users: UsersResource = realmResource.users()
    users.search("", null, null).forEach((u: UserRepresentation) => users.get(u.getId()).remove())
    for (user <- Feeders.totalUsers) {
      val u = users.create(user.toRepresentation)
      val id: String = getUserId(u)
      u.close()
      users.get(id).resetPassword(user.getCredentials);
    }
    keycloak.close()
  }

  def getUserId(u: Response): String = {
    val location = u.getHeaderString(HttpHeaders.LOCATION)
    val lastSlash = location.lastIndexOf('/');
    if (lastSlash < 0) null else location.substring(lastSlash + 1)
  }

  implicit def toConsumer[T](f: T => Unit): Consumer[T] = new Consumer[T] {
    override def accept(t: T): Unit = f.apply(t)
  }

  implicit def toPredicate[T](f: T => Boolean): Predicate[T] = new Predicate[T] {
    override def test(t: T): Boolean = f.apply(t)
  }
}
