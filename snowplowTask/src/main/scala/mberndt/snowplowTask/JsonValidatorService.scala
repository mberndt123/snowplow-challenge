package mberndt.snowplowTask

import io.circe.Json
import java.util.UUID
import cats.effect.IO
import cats.syntax.all.*
import cats.Eval
import com.github.fge.jsonschema.main.JsonSchemaFactory
import com.fasterxml.jackson.databind.ObjectMapper
import scala.jdk.CollectionConverters.*
import io.circe.parser.parse
import com.github.fge.jsonschema.core.exceptions.ProcessingException

enum ValidationResult:
  case Ok(cleaned: Json)
  case SchemaNotFound
  case InvalidDocument(error: Json)
  case JsonProcessorError(error: String)

def cleanJson(json: Json): Eval[Json] =
  Eval
    .now(json)
    .flatMap(
      _.fold(
        jsonNull = Eval.now(Json.Null),
        jsonBoolean = b => Eval.now(Json.fromBoolean(b)),
        jsonNumber = n => Eval.now(Json.fromJsonNumber(n)),
        jsonString = s => Eval.now(Json.fromString(s)),
        jsonArray = _.traverse(cleanJson).map(Json.fromValues),
        jsonObject = _.filter(a => !a._2.isNull)
          .traverse(cleanJson)
          .map(Json.fromJsonObject)
      )
    )

class JsonValidatorService(dao: SchemaDao, schemaFactory: JsonSchemaFactory):
  export dao.{insert as store, getById as retrieve}

  def validate(id: UUID, document: Json): IO[ValidationResult] =
    dao.getById(id).flatMap {
      case Some(schemaJson) =>
        val cleaned = cleanJson(document).value
        val mapper = new ObjectMapper()
        val schemaJsonNode = mapper.readTree(schemaJson.toString)
        {
          for
            schema <- IO(schemaFactory.getJsonSchema(schemaJsonNode))
            processingReport <- IO(
              schema.validate(mapper.readTree(cleaned.toString))
            )
          yield
            if processingReport.isSuccess then ValidationResult.Ok(cleaned)
            else
              val messages = processingReport.asScala.map { line =>
                parse(
                  line.asJson.toString
                ).right.get // `asJson` always returns valid json, so calling .right.get should be safe
              }.toVector
              ValidationResult.InvalidDocument(Json.fromValues(messages))
        }.recover { case err: ProcessingException =>
          ValidationResult.JsonProcessorError(err.getMessage)
        }
      case None =>
        ValidationResult.SchemaNotFound.pure[IO]
    }
