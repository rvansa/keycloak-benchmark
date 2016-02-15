import io.gatling.app.Gatling
import io.gatling.core.config.GatlingPropertiesBuilder
import org.jboss.perf.{Options, KeycloakSimulation}
import scala.collection.JavaConversions._

object Engine extends App {
	val simulationClass: String = classOf[KeycloakSimulation].getName

	if (System.getProperty("test.dump.threads", "false").toBoolean) {
		val dumper = new Thread(new Runnable {
			override def run(): Unit = {
				while (true) {
					println("Dumping all threads ");
					val start = System.currentTimeMillis();
					Thread.getAllStackTraces.toMap.foreach(pair => {
						println(Options.driver + ": " + pair._1.getName + "(" + pair._1.getState + "):")
						pair._2.foreach(ste => {
							printf("\t%s.%s (%s:%d)%n", ste.getClassName, ste.getMethodName, ste.getFileName, ste.getLineNumber)
						})
					})
					println("Dumping all threads took " + (System.currentTimeMillis() - start) + " ms");
					Thread.sleep(30000);
				}
			}
		}, "stack-dumper");
		dumper.setDaemon(true)
		dumper.start()
	}

	val props = new GatlingPropertiesBuilder
	props.dataDirectory(IDEPathHelper.directories.data.toString)
	props.resultsDirectory(IDEPathHelper.directories.results.toString)
	props.bodiesDirectory(IDEPathHelper.directories.bodies.toString)
	props.binariesDirectory(IDEPathHelper.directories.binaries.toString)
	props.simulationClass(simulationClass)

	Gatling.fromMap(props.build)
}
