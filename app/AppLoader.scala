import es.ES
import play.api.libs.logback.LogbackLoggerConfigurator
import play.api.{Application, ApplicationLoader, Logger}
import play.api.ApplicationLoader.Context


class AppLoader extends ApplicationLoader {

  override def load(context: Context): Application = {
    new LogbackLoggerConfigurator().configure(context.environment)
    val components = new AppComponents(context)

    ES.initIndex.run(components.jestClient)
    //ES.ApiKeys.search(0).run(components.jestClient).foreach(println)

    // TODO components.startKafkaConsumer()

    components.application
  }

}
