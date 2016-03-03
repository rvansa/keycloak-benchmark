package org.jboss.perf

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import javax.ws.rs.core.Response
import javax.ws.rs.{GET, POST, Path}

import com.sun.net.httpserver.HttpServer
import org.jboss.resteasy.plugins.server.sun.http.HttpContextBuilder
import org.keycloak.constants.AdapterConstants

/**
  * @author Radim Vansa &lt;rvansa@redhat.com&gt;
  */
object AppServer {
  private val address: Array[String] = Options.app.split(":")
  private val httpServer = HttpServer.create(new InetSocketAddress(address(0), address(1).toInt), 100)
  private val contextBuilder = new HttpContextBuilder()
  contextBuilder.getDeployment().getActualResourceClasses().add(classOf[AppServer])
  private val context = contextBuilder.bind(httpServer)

  def main(args: Array[String]): Unit = {
    httpServer.start()
    val timeout = Options.rampUp + Options.duration + Options.rampDown + 10;
    Thread.sleep(TimeUnit.SECONDS.toMillis(timeout))
    httpServer.stop(0);
  }
}

@Path("/admin")
class AppServer {
  @GET
  @POST
  @Path(AdapterConstants.K_LOGOUT)
  def logout(): Response = {
    Response.ok().build()
  }

  @GET
  @POST
  @Path(AdapterConstants.K_VERSION)
  def version(): Response = {
    Response.ok().build()
  }

  @GET
  @POST
  @Path(AdapterConstants.K_PUSH_NOT_BEFORE)
  def pushNotBefore(): Response = {
    Response.ok().build()
  }

  @GET
  @POST
  @Path(AdapterConstants.K_QUERY_BEARER_TOKEN)
  def queryBearerToken(): Response = {
    Response.ok().build()
  }

  @GET
  @POST
  @Path(AdapterConstants.K_TEST_AVAILABLE)
  def testAvailable(): Response = {
    Response.ok().build()
  }

}
