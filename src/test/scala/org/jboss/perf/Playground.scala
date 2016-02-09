package org.jboss.perf

import io.gatling.core.session._
import org.keycloak.adapters.spi.HttpFacade.Cookie
import io.gatling.core.validation._
import io.gatling.core.Predef.Session


/**
  * // TODO: Document this
  *
  * @author Radim Vansa &lt;rvansa@redhat.com&gt;
  */
object Playground {
  def parseCookies(header: Expression[String]): Expression[List[Cookie]] = {
    s: Session => header(s).flatMap(h => h.split('\n').map(c => parseCookie(c)).toList.success)
  }

  def parseCookie(str: String) = {
    var name: String = null
    var value: String = null
    var version: Int = 0
    var domain: String = null
    var path: String = null
    for (part <- str.split(';')) {
      var eq : Int = part.indexOf('=')
      var key = if (eq < 0) part.trim else part.substring(0, eq)
      var v = if (eq >= 0) part.substring(eq + 1) else ""
      if (name == null) {
        name = key;
        value = v;
      } else key match {
        case "Version" => version = v.toInt;
        case "Domain" => domain = v;
        case "Path" => path = v;
        case _ => {} // ignore expiration etc.
      }
    }
    new Cookie(name, value, version, domain, path)
  }
}
