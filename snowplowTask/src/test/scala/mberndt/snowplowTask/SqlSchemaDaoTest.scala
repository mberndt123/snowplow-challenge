package mberndt.snowplowTask

import munit.CatsEffectSuite
import doobie.h2.H2Transactor
import cats.effect.IO
import scala.concurrent.ExecutionContext.global
import java.util.UUID
import io.circe.Json
import cats.syntax.all.*

class SqlSchemaDaoTest extends CatsEffectSuite:
  val testDb =
    initDb("jdbc:h2:mem:foobar", global).map(SqlSchemaDao.apply)
  val json1 = Json.fromString("a")
  val json2 = Json.fromString("b")

  test("write schemas to db and find by UUID") {
    testDb
      .use { dao =>
        for
          uuid1 <- randomUuid
          uuid2 <- randomUuid
          _ <- dao.insert(uuid1, json1)
          _ <- dao.insert(uuid2, json2)
          res1 <- dao.getById(uuid1)
          res2 <- dao.getById(uuid2)
        yield (res1, res2)
      }
      .assertEquals((json1.some, json2.some))
  }

  test("overwrite when inserting more than once") {
    testDb
      .use { dao =>
        for
          uuid <- randomUuid
          _ <- dao.insert(uuid, json1)
          _ <- dao.insert(uuid, json2)
          res <- dao.getById(uuid)
        yield res
      }
      .assertEquals(json2.some)
  }
