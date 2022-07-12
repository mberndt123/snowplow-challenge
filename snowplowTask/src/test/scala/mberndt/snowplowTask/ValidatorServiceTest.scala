package mberndt.snowplowTask

import munit.CatsEffectSuite
import java.nio.file.Files
import io.circe.literal.*
import scala.concurrent.ExecutionContext.global
import com.github.fge.jsonschema.main.JsonSchemaFactory

val testSchema = json"""{
  "$$schema": "http://json-schema.org/draft-04/schema#",
  "type": "object",
  "properties": {
    "source": {
      "type": "string"
    },
    "destination": {
      "type": "string"
    },
    "timeout": {
      "type": "integer",
      "minimum": 0,
      "maximum": 32767
    },
    "chunks": {
      "type": "object",
      "properties": {
        "size": {
          "type": "integer"
        },
        "number": {
          "type": "integer"
        }
      },
      "required": ["size"]
    }
  },
  "required": ["source", "destination"]
}"""

val testData = json"""{
  "source": "/home/alice/image.iso",
  "destination": "/mnt/storage",
  "timeout": null,
  "chunks": {
    "size": 1024,
    "number": null
  }
}"""

val testDataCleaned = json"""{
  "source": "/home/alice/image.iso",
  "destination": "/mnt/storage",
  "chunks": {
    "size": 1024
  }
}"""

class ValidatorServiceTest extends CatsEffectSuite:
  val validatorResource = initDb("jdbc:h2:mem:foobar", global).map(tx =>
    JsonValidatorService(SqlSchemaDao(tx), JsonSchemaFactory.byDefault())
  )

  test("happy path") {
    validatorResource
      .use { validator =>
        for
          uuid <- randomUuid
          _ <- validator.store(uuid, testSchema)
          res <- validator.validate(uuid, testData)
        yield res
      }
      .assertEquals(ValidationResult.Ok(testDataCleaned))
  }

  test("detect missing schema") {
    validatorResource
      .use { validator =>
        for
          uuid <- randomUuid
          res <- validator.validate(uuid, testData)
        yield res
      }
      .assertEquals(ValidationResult.SchemaNotFound)
  }

  test("detect invalid document") {
    validatorResource
      .use { validator =>
        for
          uuid <- randomUuid
          _ <- validator.store(uuid, testSchema)
          res <- validator.validate(uuid, json"{}")
        yield res match
          case ValidationResult.InvalidDocument(_) => true
          case _                                   => false
      }
      .assertEquals(true)
  }

  test("detect invalid schema or other processing errors") {
    validatorResource
      .use { validator =>
        for
          uuid <- randomUuid
          _ <- validator.store(uuid, json"""{"type": "foo"}""")
          res <- validator.validate(uuid, json"{}")
        yield res match
          case ValidationResult.JsonProcessorError(_) => true
          case _                                      => false
      }
      .assertEquals(true)
  }
