package org.jboss.perf.model

import org.keycloak.representations.idm.{CredentialRepresentation, UserRepresentation}
import java.util.Collections

/**
  * @author Radim Vansa &lt;rvansa@redhat.com&gt;
  */
case class User(val username: String, val password: String, var id: String) {
  def this(map: Map[String, String]) {
    this(map("username"), map("password"), map("id"))
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
    representation
  }
}
