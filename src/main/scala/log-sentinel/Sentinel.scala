import cats.syntax.all.*
import cats.effect.{IO, IOApp, Sync, Async}
import cats.effect.ExitCode
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
object LogSentinel extends IOApp:
  override def run(args: List[String]): IO[ExitCode] =
    for
      paths = pathArgs(args)
      _ <- IO.raiseWhen(paths.length == 0)(
        new IllegalArgumentException(
          "provide directory/s for LogSentinel or leave to empty to default to 'logs' directory"
        )
      )
      _ <- IO(paths.map(n => new File(n)).map(f => f.exists()).foreach(println))
    yield (ExitCode(0))

  private def pathArgs(args: List[String]): List[String] =
    val fileArgs = args.filterNot(a => a.startsWith("-"))
    if fileArgs.isEmpty then List("logs") else fileArgs

  def readStream[F[_]: Sync](f: File): Resource[F, FileInputStream] =
    Resource.make(Sync[F].blocking(new FileInputStream(f))) {
      inStream =>
        Sync[F].blocking(inStream.close()).handleErrorWith(_ => Sync[F].unit)
    }

  def processFile[F[_]: Sync](file: File) =
    readStream(file).use { stream =>
      Sync[F].blocking(cats.effect.std.Console[IO].println(s"$stream"))
    }
