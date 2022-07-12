package mberndt.snowplowTask

import munit.CatsEffectSuite
import scala.concurrent.ExecutionContext.global
import com.github.fge.jsonschema.main.JsonSchemaFactory
import org.http4s.client.Client
import cats.effect.IO
import cats.effect.kernel.Resource
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.client.dsl.io.*
import org.http4s.Method.*
import org.http4s.Uri
import io.circe.Json
import cats.syntax.all.*
import io.circe.literal.*
import org.http4s.syntax.all.*
import org.http4s.Status.*
import org.http4s.headers.`Content-Type`
import org.http4s.MediaType
import org.http4s.EntityEncoder

class ServerTest extends CatsEffectSuite:
  val testClient = ResourceSuiteLocalFixture(
    "test-server",
    initDb("jdbc:h2:mem:foobar", global).map { tx =>
      val dao = SqlSchemaDao(tx)
      val validatorService =
        JsonValidatorService(dao, JsonSchemaFactory.byDefault())
      val s = server(validatorService)
      Client.fromHttpApp(s)
    }
  )

  override def munitFixtures: Seq[Fixture[?]] =
    Seq(testClient)

  test("test schema upload and download") {
    {
      for
        uuid <- randomUuid
        uploadResponseJson <- testClient().expect[Json](
          POST(testSchema, uri"schema" / uuid)
        )
        downloadResponseJson <- testClient().expect[Json](uri"schema" / uuid)
      yield uploadResponseJson === json"""{
        "action": "uploadSchema",
        "id": $uuid,
        "status": "success",
        "data": null
      }""" &&
        downloadResponseJson === json"""{
        "action": "downloadSchema",
        "id": $uuid,
        "status": "success",
        "data": $testSchema
      }"""
    }.assert
  }

  test("test validation") {
    {
      for
        uuid <- randomUuid
        _ <- testClient().expect[Json](
          POST(testSchema, uri"schema" / uuid)
        )
        validationResponseJson <- testClient().expect[Json](
          POST(testData, uri"validate" / uuid)
        )
      yield validationResponseJson === json"""{
        "action" : "validateDocument",
        "id" : $uuid,
        "status" : "success",
        "data" : $testDataCleaned
      }"""
    }.assert
  }

  test("respond with valid json when confronted with non-json input") {
    {

      for
        uuid <- randomUuid
        errorResponse <- testClient()
          .run(
            POST("not valid json", uri"schema" / uuid)(
              EntityEncoder.stringEncoder
            )
              .withContentType(`Content-Type`(MediaType.application.json))
          )
          .use { req =>
            IO(assertEquals(req.status, BadRequest)) >> req.as[Json]
          }
      yield errorResponse
    }.assertEquals(json"""{
      "status": "failure",
      "error":"Malformed message body: Invalid JSON"
    }""")

  }
