package io.gatling.keycloak

import io.gatling.core.session._

/**
  * @author Radim Vansa &lt;rvansa@redhat.com&gt;
  */
object Predef {
  def oauth(requestName: Expression[String]) = new Oauth(requestName)
  def admin(requestName: Expression[String]) = new Admin(requestName)
}
