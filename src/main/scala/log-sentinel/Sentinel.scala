import cats.syntax.all.*
import cats.effect.{IO, IOApp, Sync}
import java.sql.Timestamp
import java.io.File
import cats.effect.kernel.Resource
import java.io.FileInputStream

//  {"timestamp":"2026-04-27T19:55:10Z","level":"INFO","source":"api","message":"user logged in"}

enum LogLevel:
  case INFO
  case WARN
  case ERROR

final case class LogEntry(
    timestamp: Timestamp,
    level: LogLevel,
    source: String,
    message: String
)

/** LogSentinel parses text and json files representing logs, and analyzes and
  * summarizes the data. If args is left empty, the program assumes the logs are
  * in the `logs` directory. If args are provided, it will look for logs in the
  * directories specified.
  *
  * @param args
  *   -> Lists of extra directories to look for logs. Make sure directory is in
  *   scope for the program and use correct dir syntax e.g args = List("logs",
  *   "somefolder/logs")
  * @return
  */
object LogSentinel extends IOApp.Simple:

  def run: IO[Unit] =
    for
      files <- getLogs()
      _ <- files.traverse_(processFile)
    yield ()

  private def getLogs(): IO[List[File]] =
    IO.blocking {
      Option(new File("logs").listFiles()).toList.flatten.filter(_.isFile)
    }

  def readStream[F[_]: Sync](f: File): Resource[F, FileInputStream] =
    Resource.make(Sync[F].blocking(new FileInputStream(f))) {
      inStream =>
        Sync[F].blocking(inStream.close()).handleErrorWith(_ => Sync[F].unit)
    }

  def processFile(file: File): IO[Unit] =
    readStream[IO](file).use { fis =>
      IO.println(s"opened: ${file.getPath}")
    }
