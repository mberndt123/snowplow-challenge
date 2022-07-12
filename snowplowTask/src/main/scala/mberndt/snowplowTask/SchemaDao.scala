package mberndt.snowplowTask

import java.util.UUID
import io.circe.Json
import cats.effect.IO
import doobie.util.transactor.Transactor
import doobie.syntax.all.*
import doobie.h2.implicits.*
import doobie.h2.syntax.*
import doobie.h2.circe.json.implicits.*
import doobie.util.log.LogHandler

trait SchemaDao:
  def getById(uuid: UUID): IO[Option[Json]]
  def insert(uuid: UUID, json: Json): IO[Unit]

object SqlSchemaDao:
  def apply(transactor: Transactor[IO])(implicit log: LogHandler): SchemaDao =
    new SchemaDao:
      def getById(uuid: UUID) =
        sql"select schema from json_schemas where id = $uuid"
          .query[Json]
          .option
          .transact(transactor)

      def insert(uuid: UUID, json: Json): IO[Unit] =
        sql"merge into json_schemas(id, schema) values($uuid, $json)".update.run
          .transact(transactor)
          .void
