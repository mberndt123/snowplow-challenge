package mberndt.snowplowTask

import java.util.UUID
import io.circe.Json
import org.http4s.dsl.io.*
import org.http4s.HttpRoutes
import cats.effect.IO
import org.http4s.Status.*
import cats.syntax.all.*
import org.http4s.syntax.all.*
import io.circe.Encoder
import io.circe.generic.semiauto.*
import org.http4s.server.middleware.ErrorHandling
import org.http4s.MessageFailure
import org.http4s.HttpVersion
import org.http4s.Response
import io.circe.syntax.*
import org.http4s.EntityEncoder
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*

import org.http4s.circe.CirceInstances
import cats.data.NonEmptyList
import org.http4s.DecodeFailure
import io.circe.DecodingFailure
import io.circe.literal.*
import org.http4s.MessageBodyFailure
import cats.data.Kleisli
import org.http4s.Request

case class ValidatorResponse(
    action: String,
    id: UUID,
    status: String,
    data: Option[Json]
) derives Encoder.AsObject

def server(
    validatorService: JsonValidatorService
): Kleisli[cats.effect.IO, Request[cats.effect.IO], Response[cats.effect.IO]] =
  ErrorHandling {
    HttpRoutes
      .of[IO] {
        case GET -> Root / "schema" / UUIDVar(uuid) =>
          validatorService.retrieve(uuid).flatMap {
            case Some(schema) =>
              Ok(
                ValidatorResponse(
                  action = "downloadSchema",
                  id = uuid,
                  status = "success",
                  data = schema.some
                )
              )
            case None =>
              NotFound(
                ValidatorResponse(
                  action = "downloadSchema",
                  id = uuid,
                  status = "failure",
                  data = none
                )
              )
          }

        case req @ POST -> Root / "schema" / UUIDVar(uuid) =>
          for
            schemaJson <- req.as[Json]
            _ <- validatorService.store(uuid, schemaJson)
            res <- Ok(
              ValidatorResponse(
                action = "uploadSchema",
                id = uuid,
                status = "success",
                data = none
              )
            )
          yield res

        case req @ POST -> Root / "validate" / UUIDVar(uuid) =>
          for
            documentJson <- req.as[Json]
            validationResult <- validatorService.validate(uuid, documentJson)
            response <-
              def resp(status: String, data: Option[Json]) = ValidatorResponse(
                action = "validateDocument",
                id = uuid,
                status = status,
                data = data
              )

              validationResult match
                case ValidationResult.Ok(cleaned) =>
                  Ok(resp("success", cleaned.some))
                case ValidationResult.SchemaNotFound =>
                  NotFound(resp("failure", none))
                case ValidationResult.JsonProcessorError(err) =>
                  // This means that something that isn't a JSON schema was stored in
                  // the database, and validating that beforehand is deemed unnecessary
                  // in the specification. So I'll just throw up here :o)
                  InternalServerError(resp("failure", none))
                case ValidationResult.InvalidDocument(err) =>
                  Ok(resp("error", err.some))
          yield response
      }
      .adaptErr { case err: MessageFailure =>
        new MessageFailure:
          export err.{toHttpResponse as _, *}

          // should always return a JSON body
          def toHttpResponse[F[_]](httpVersion: HttpVersion): Response[F] =
            val entityEncoder = EntityEncoder[F, Json]
            Response(
              BadRequest,
              headers = entityEncoder.headers,
              body = entityEncoder
                .toEntity(
                  json"""{"status": "failure", "error": ${err.message}}""".asJson
                )
                .body
            )
      }
  }.orNotFound
