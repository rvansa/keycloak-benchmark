import io.gatling.app.Gatling
import io.gatling.core.config.GatlingPropertiesBuilder
import org.jboss.perf.KeycloakSimulation

object Engine extends App {
	val simulationClass: String = classOf[KeycloakSimulation].getName

	val props = new GatlingPropertiesBuilder
	props.dataDirectory(IDEPathHelper.directories.data.toString)
	props.resultsDirectory(IDEPathHelper.directories.results.toString)
	props.bodiesDirectory(IDEPathHelper.directories.bodies.toString)
	props.binariesDirectory(IDEPathHelper.directories.binaries.toString)
	props.simulationClass(simulationClass)

	Gatling.fromMap(props.build)
}
