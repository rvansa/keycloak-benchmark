package org.jboss.perf.model

import java.util
import java.util.Collections
import org.keycloak.representations.idm.{RoleRepresentation, CredentialRepresentation, UserRepresentation}
import scala.collection.JavaConverters._

/**
  * @author Radim Vansa &lt;rvansa@redhat.com&gt;
  */
case class User(val username: String, val password: String, var id: String, val realmRoles: List[String]) {

  def this(map: Map[String, String]) {
    this(map("username"), map("password"), map("id"), List())
  }

  def getCredentials: CredentialRepresentation = {
    var credentials = new CredentialRepresentation
    credentials.setType(CredentialRepresentation.PASSWORD)
    credentials.setTemporary(false)
    credentials.setValue(password)
    credentials
  }

  def toMap: Map[String, String] =
    Map(("username", username), ("password", password), ("id", id))

  def toRepresentation: UserRepresentation = {
    var representation = new UserRepresentation
    // Id is ignored
    representation.setUsername(username)
    representation.setEnabled(true)
    // Actually the credentials will be ignored on server
    representation.setCredentials(Collections.singletonList(getCredentials))
    representation.setRealmRoles(realmRoles.asJava)
    representation
  }

  def getRealmRoles(): util.List[RoleRepresentation] = {
    realmRoles.map(r => {
      val role = new RoleRepresentation(r, "", false);
      role.setId(r)
      role
    }).asJava
  }
}
