package org.jboss.perf

import java.util.concurrent.atomic.AtomicInteger
import java.util.function.{Predicate, Consumer}
import javax.ws.rs.core.{Response, HttpHeaders}

import org.jboss.perf.model.User
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.UsersResource
import org.keycloak.representations.idm.RealmRepresentation
import org.keycloak.util.JsonSerialization

/**
  * @author Radim Vansa &lt;rvansa@redhat.com&gt;
  */
object Loader {
  // TODO: catch exceptions
  val realmRepresentation: RealmRepresentation = JsonSerialization.readValue(getClass.getResourceAsStream("/keycloak/benchmark-realm.json"), classOf[RealmRepresentation])
  val realmName = realmRepresentation.getRealm
  val client = realmRepresentation.getClients().stream().findFirst().get()
  val loadCounter = new AtomicInteger()

  realmRepresentation.setPublicKey(Security.PublicKey)
  realmRepresentation.setPrivateKey(Security.PrivateKey)
  realmRepresentation.setCertificate(Security.Certificate)

  def connection(host: String = Options.hosts(0)): Keycloak = {
    Keycloak.getInstance("http://" + host + "/auth", "master", "admin", "admin", "admin-cli")
  }

  val connection: ThreadLocal[Keycloak] = new ThreadLocal[Keycloak] {
    override def initialValue(): Keycloak = {
      connection() // the connection leaks, but we don't care
    }
  }

  def main(args: Array[String]) {
    val keycloak = connection()
    var optRealm = keycloak.realms().findAll().stream().filter((r: RealmRepresentation) => r.getRealm.equals(realmRepresentation.getRealm)).findFirst()
    if (optRealm.isPresent) {
      keycloak.realm(realmRepresentation.getRealm).remove()
    }
    keycloak.realms().create(realmRepresentation)
    Feeders.totalUsers.par.foreach(addUser)
  }

  def addUser(user: User): Unit = {
    while (true) {
      try {
        val realmResource = connection.get().realm(realmRepresentation.getRealm)
        val users = realmResource.users()
        val u = users.create(user.toRepresentation)
        val id: String = getUserId(u)
        u.close()
        users.get(id).resetPassword(user.getCredentials);
        val counter: Int = loadCounter.incrementAndGet()
        if (counter % 100 == 0) {
          // damned scala
          System.err.printf("Loaded %s/%s users\n", String.valueOf(counter), String.valueOf(Feeders.totalUsers.length))
        }
        return
      } catch {
        case e: Exception => {
          this.connection.get().close()
          this.connection.set(connection())
        }
      }
    }
  }

  def getUserId(u: Response): String = {
    val location = u.getHeaderString(HttpHeaders.LOCATION)
    if (location == null) {
      throw new IllegalStateException(u.getHeaders.toString)
    }
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
