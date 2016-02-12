import io.gatling.app.{ConfigOverrides, Gatling}
import io.gatling.core.config.GatlingPropertiesBuilder

/**
  * @author Radim Vansa &lt;rvansa@redhat.com&gt;
  */
object Report extends App {
  val props = new GatlingPropertiesBuilder
  props.resultsDirectory(System.getProperty("test.report"))
  props.reportsOnly("./")
  Gatling.fromMap(props.build)
}
