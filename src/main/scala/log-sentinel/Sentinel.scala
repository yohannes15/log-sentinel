import cats.syntax.all.*
import cats.effect.{IO, IOApp, Sync}
import java.sql.Timestamp
import cats.effect.kernel.Resource
import os.Path
import geny.Generator
import scala.util.matching.Regex
import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import cats.data.ValidatedNec
import cats.data.Validated.Valid
import cats.data.Validated.Invalid
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
    // flexible formatter that handles ISO dates with optional time offsets (Z, +01:00, etc.)
    val formatter = new DateTimeFormatterBuilder()
      .append(
        DateTimeFormatter.ISO_LOCAL_DATE_TIME
      ) // Parses the date and time part
      .optionalStart()
      .appendOffsetId() // Handles the 'Z' or offset if present
      .optionalEnd()
      .toFormatter()

    // Parse using the custom formatter and convert to a java.sql.Timestamp
    Timestamp.from(java.time.OffsetDateTime.parse(s, formatter).toInstant)

  def fromString(s: String): Either[String, LogEntry] =
    s match
      case logLinePattern(tsStr, lvlStr, src, msg) =>
        lvlStr match
          case "INFO" =>
            Right(LogEntry(toTimeStamp(tsStr), LogLevel.INFO, src, msg))
          case "WARN" =>
            Right(LogEntry(toTimeStamp(tsStr), LogLevel.WARN, src, msg))
          case "ERROR" =>
            Right(LogEntry(toTimeStamp(tsStr), LogLevel.ERROR, src, msg))
          case other => Left(s"Unknown log level: $other on line: $s")
      case _ => Left(s"Can't parse log line: $s")

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
      _ <- logEntries.zipWithIndex.traverse {
        case ((path, errors, entries), _) =>
          processFileResult(errors, entries, path)
      }
    yield ()

  private def processFileResult(
      errors: List[String],
      entries: List[LogEntry],
      path: Path
  ): IO[Unit] =
    val filename = path.last // gets filename from path
    for
      _ <- if (errors.nonEmpty) then
        IO.println(s"[$filename] \nerrors: \n${errors.mkString("\n")}")
      else IO.unit
      counts = getLogLevelCounts(entries)
      _ <- IO.println(s"[$filename] summary: $counts")
    yield ()

  private def getLogs(): IO[List[Path]] = IO.blocking {
    os.list(os.pwd / "logs").filter(os.isFile).toList
  }

  def readStream[F[_]: Sync](f: Path): Resource[F, Generator[String]] =
    Resource.make(Sync[F].blocking(os.read.lines.stream(f)))(_ => Sync[F].unit)

  def getLogEntries(file: Path): IO[(Path, List[String], List[LogEntry])] =
    readStream[IO](file).use { gen =>
      val lines = gen.map(_.trim).filter(_.nonEmpty).toList
      // Partition takes a function that returns Either[L, R]
      // It returns a tuple: (List[L], List[R])
      val linesProcessed: List[Either[String, LogEntry]] =
        lines.map(LogEntry.fromString)
      val (errors, entries): (List[String], List[LogEntry]) =
        linesProcessed.partitionMap(identity)

      IO.pure((file, errors, entries))
    }

def getLogLevelCounts(entries: List[LogEntry]): Map[LogLevel, Int] =
  entries.groupBy(_.level).view.mapValues(_.size).toMap
