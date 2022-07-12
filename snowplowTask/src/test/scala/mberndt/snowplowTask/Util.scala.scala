package mberndt.snowplowTask
import cats.effect.IO
import java.util.UUID

val randomUuid = IO(UUID.randomUUID())
