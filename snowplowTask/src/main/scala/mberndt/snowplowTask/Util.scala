package mberndt.snowplowTask

import doobie.util.transactor
import cats.effect.{Resource, IO}
import doobie.util.transactor.Transactor
import scala.concurrent.ExecutionContext
import doobie.h2.H2Transactor
import cats.syntax.all.*
import org.flywaydb.core.Flyway
import doobie.util.log.LogHandler

given LogHandler = LogHandler(_ => ())

def initDb(
    jdbcUrl: String,
    ec: ExecutionContext
): Resource[IO, Transactor[IO]] =
  H2Transactor
    .newH2Transactor[IO](
      jdbcUrl,
      "sa",
      "",
      ec
    )
    .flatTap { xa =>
      Resource.eval {
        IO {
          val flyway = Flyway.configure().dataSource(xa.kernel).load()
          flyway.migrate()
        }
      }
    }
