package org.jboss.perf

import scala.collection.JavaConverters._

import java.util.Collections
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.{Predicate, Consumer}
import javax.ws.rs.core.{Response, HttpHeaders}

import org.jboss.perf.model.User
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.{UserResource, UsersResource}
import org.keycloak.representations.idm.{UserRepresentation, RoleRepresentation, RealmRepresentation}
import org.keycloak.util.JsonSerialization

/**
  * @author Radim Vansa &lt;rvansa@redhat.com&gt;
  */
object Loader {
  // TODO: catch exceptions
  val realmRepresentation: RealmRepresentation = JsonSerialization.readValue(getClass.getResourceAsStream("/keycloak/benchmark-realm.json"), classOf[RealmRepresentation])

  val client = realmRepresentation.getClients().stream().findFirst().get()
  client.setRedirectUris(Collections.singletonList("http://" + Options.app + "/app"))
  client.setBaseUrl("http://" + Options.app + "/")
  client.setAdminUrl("http://" + Options.app + "/admin")

  realmRepresentation.getRoles.getRealm.addAll((0 until Options.userRoles).map(i => {
    val role = new RoleRepresentation("user_role_" + i, "", false)
    role.setId("user_role_" + i)
    role
  }).asJava)
  val realmName = realmRepresentation.getRealm

  val loadCounter = new AtomicInteger()

  realmRepresentation.setPublicKey(Security.PublicKey)
  realmRepresentation.setPrivateKey(Security.PrivateKey)
  realmRepresentation.setCertificate(Security.Certificate)

  def connection(host: String = Options.servers(0)): Keycloak = {
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
    if (Options.fullReload) {
      if (optRealm.isPresent) {
        keycloak.realm(realmName).remove()
      }
      keycloak.realms().create(realmRepresentation)
      Feeders.totalUsers.par.foreach(addUser)
    } else {
      if (!optRealm.isPresent) {
        keycloak.realms().create(realmRepresentation)
        Feeders.totalUsers.par.foreach(addUser)
      } else {
        keycloak.realm(realmName).users().search(null, null, "Active", null, null, null).asScala.par.foreach(removeUser)
        Feeders.activeUsers.par.foreach(addUser)
      }
    }
  }

  def withUsers(message: String, invocation: UsersResource => Unit) {
    var users = connection.get().realm(realmRepresentation.getRealm).users()
    for (i <- 0 until 100) {
      try {
        invocation(users);
        return
      } catch {
        case e: Exception => {
          System.err.println(s"Failed to ${message}: ")
          e.printStackTrace();
          this.connection.get().close()
          val conn = connection();
          this.connection.set(conn)
          users = conn.realm(realmRepresentation.getRealm).users();
        }
      }
    }
    System.err.println(s"Failed 100 attempts to ${message}");
    System.exit(1);
  }

  def addUser(user: User): Unit = {
    withUsers("create new user", users => {
      val response = users.create(user.toRepresentation)
      var id: String = getUserId(response)
      response.close()
      if (id == null) {
        println("Expiration set to " + new SimpleDateFormat("HH:mm:ss").format(new Date(connection.get().tokenManager().getAccessToken.getExpiresIn)))
        val existing = users.search(user.username, null, null, null, null, null)
        if (existing == null || existing.isEmpty) {
          throw new IllegalStateException(s"User ${user.username} exists but we could not find any")
        } else if (existing.size() > 1) {
          throw new IllegalStateException(s"Multiple users with username ${user.username}: " + existing)
        } else {
          id = existing.get(0).getId;
        }
      }
      val userResource: UserResource = users.get(id)
      userResource.resetPassword(user.getCredentials);
      userResource.roles().realmLevel().add(user.getRealmRoles())
      val counter: Int = loadCounter.incrementAndGet()
      if (counter % 100 == 0) {
        // damned scala
        System.err.println("%s, Loaded %s/%s users".format(new SimpleDateFormat("HH:mm:ss").format(new Date()), counter, Feeders.totalUsers.length))
      }
    })
  }

  def getUserId(response: Response): String = {
    val location = response.getHeaderString(HttpHeaders.LOCATION)
    if (location == null) {
      System.err.println("Failed to create user (no location): \nStatus: " + response.getStatusInfo()
        + "\nHeaders: " + response.getHeaders.toString + "\nEntity: " + response.getEntity)
      return null;
    }
    val lastSlash = location.lastIndexOf('/');
    if (lastSlash < 0) null else location.substring(lastSlash + 1)
  }

  def removeUser(user: UserRepresentation): Unit = {
    withUsers("remove old user", users => {
        users.get(user.getId).remove()
    });
  }

  implicit def toConsumer[T](f: T => Unit): Consumer[T] = new Consumer[T] {
    override def accept(t: T): Unit = f.apply(t)
  }

  implicit def toPredicate[T](f: T => Boolean): Predicate[T] = new Predicate[T] {
    override def test(t: T): Boolean = f.apply(t)
  }
}
