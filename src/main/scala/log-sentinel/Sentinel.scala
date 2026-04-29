import cats.syntax.all.*
import cats.effect.{IO, IOApp, Sync}
import java.sql.Timestamp
import cats.effect.kernel.Resource
import os.Path
import geny.Generator
import scala.util.matching.Regex

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

object LogEntry:
  private val logLinePattern: Regex = """^(\S+)\s+(\S+)\s+(\S+)\s+(.+)$""".r

  def toTimeStamp(s: String): Timestamp =
    Timestamp.from(java.time.Instant.parse(s))

  def fromFileLine(line: String): IO[LogEntry] =
    line match
      case logLinePattern(tsStr, lvlStr, src, msg) =>
        lvlStr match
          case "INFO" => IO.pure(
              LogEntry(toTimeStamp(tsStr), LogLevel.INFO, src, msg)
            )
          case "WARN" => IO.pure(
              LogEntry(toTimeStamp(tsStr), LogLevel.WARN, src, msg)
            )
          case "ERROR" => IO.pure(
              LogEntry(toTimeStamp(tsStr), LogLevel.ERROR, src, msg)
            )
          case other => IO.raiseError(
              new IllegalArgumentException(s"Unknown log level: $other")
            )
      case _ =>
        IO.raiseError(
          IllegalArgumentException(s"Can't parse log line: $line")
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
      logEntries <- files.traverse(getLogEntries)
      _ <- IO.println(logEntries)
    yield ()

  private def getLogs(): IO[List[Path]] = IO.blocking {
    os.list(os.pwd / "logs").filter(os.isFile).toList
  }

  def readStream[F[_]: Sync](f: Path): Resource[F, Generator[String]] =
    Resource.make(Sync[F].blocking(os.read.lines.stream(f)))(_ => Sync[F].unit)
    // inStream =>
    //  Sync[F].blocking(inStream.close()).handleErrorWith(_ => Sync[F].unit)

  def getLogEntries(file: Path): IO[List[LogEntry]] =
    readStream[IO](file).use { gen =>
      gen.map(
        _.trim
      ).filter(_.nonEmpty).toList.traverse(LogEntry.fromFileLine)
    }
