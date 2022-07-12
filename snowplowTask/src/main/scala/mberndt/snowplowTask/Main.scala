package mberndt.snowplowTask

import com.monovore.decline.effect.CommandIOApp
import com.monovore.decline.Opts
import cats.effect.{ExitCode, IO, Resource}
import cats.syntax.all.*
import java.nio.file.Path
import doobie.util.ExecutionContexts
import doobie.h2.H2Transactor
import org.flywaydb.core.Flyway
import com.github.fge.jsonschema.main.JsonSchemaFactory
import org.http4s.ember.server.EmberServerBuilder
import com.comcast.ip4s.Port

object Main extends CommandIOApp(BuildInfo.name, "", true, BuildInfo.version):
  override def main: Opts[IO[ExitCode]] =
    Opts.argument[Path]("database-file").map { databaseFile =>
      {
        for
          ec <- ExecutionContexts.fixedThreadPool[IO](32)
          tx <- initDb(s"jdbc:h2:file:$databaseFile", ec)
          schemaDao = SqlSchemaDao(tx)
          validator = JsonValidatorService(
            schemaDao,
            JsonSchemaFactory.byDefault()
          )
          _ <- EmberServerBuilder
            .default[IO]
            .withPort(Port.fromInt(8080).get)
            .withHttpApp(server(validator))
            .build
        yield ()
      }.useForever
    }
