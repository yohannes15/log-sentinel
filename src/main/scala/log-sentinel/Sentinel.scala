import cats.syntax.all.*
import cats.effect.{IO, IOApp, Sync}
import cats.effect.implicits.*
import cats.data.ValidatedNec
import cats.data.Validated.Valid
import cats.data.Validated.Invalid
import cats.effect.kernel.Resource
import java.sql.Timestamp
import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import os.Path
import scala.util.matching.Regex
import geny.Generator
import io.circe.{Encoder, Decoder}
import io.circe.parser.{decode, parse}
import scala.util.Try
import cats.effect.Ref
import cats.effect.ExitCode
import cats.kernel.Monoid

//  {"timestamp":"2026-04-27T19:55:10Z","level":"INFO","source":"api","message":"user logged in"}

enum LogLevel:
  case INFO, WARN, ERROR

object LogLevel:
  given Decoder[LogLevel] = Decoder.decodeString.emap { s =>
    Try(LogLevel.valueOf(s.toUpperCase)).toEither.leftMap(_ =>
      s"'$s' is not a valid LogLevel. Expected one of ${LogLevel.values.mkString(", ")}"
    )
  }

final case class Summary(
    levelCounts: Map[LogLevel, Int],
    sourceCounts: Map[String, Int],
    errors: List[String]
)

object Summary:
  val empty = Summary(Map.empty, Map.empty, List.empty)
  given Monoid[Summary] with
    def empty: Summary = Summary.empty
    def combine(x: Summary, y: Summary): Summary =
      Summary(
        x.levelCounts |+| y.levelCounts,
        x.sourceCounts |+| y.sourceCounts,
        x.errors ::: y.errors
      )

final case class LogEntry(
    timestamp: Timestamp,
    level: LogLevel,
    source: String,
    message: String
) derives Decoder

object LogEntry:
  private val logLinePattern: Regex = """^(\S+)\s+(\S+)\s+(\S+)\s+(.+)$""".r

  /** When a library like Circe doesn't know how to handle a specific type, we
    * provde a custom decoder, using a given instance. Below tells circe,
    * "Whenever you see a Timestamp, treat it as a String and use toTimeStamp
    * function String -> Timestamp"
    */
  given Decoder[Timestamp] = Decoder.decodeString.map(toTimeStamp)

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

  def fromJson(s: String): Either[String, LogEntry] =
    decode[LogEntry](s).leftMap(error => error.getMessage)

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

  def run(args: List[String]): IO[ExitCode] =
    for
      _ <- IO.raiseWhen(
        args.isEmpty
      )(new IllegalArgumentException("folder name not provided"))
      // total counter state
      summaryRef <- Ref.of[IO, Summary](Summary.empty)
      files <- getLogs(args)
      // process files in parallel
      logEntries <- files.parTraverseN(2)(getLogEntries)
      _ <- logEntries.traverse { case (path, errors, entries) =>

        for
          fileSummary <- getFileSummary(errors, entries, path)

          /** Update the shared totalState. We use Ref because the files are
            * processed in parallel, and Ref provides a thread-safe way to
            * update shared state without race conditions. The |+| syntax
            * (semigroup combine) merges the two maps and sums the values for
            * common keys. Ref is useful for Streaming Updates, Progress
            * Reports, ...
            */
          _ <- summaryRef.update(summary => summary |+| fileSummary)
        yield ()
      }
      // get the final aggregated totals
      summary <- summaryRef.get
      _ <- IO.println(s"***********\nSummary: ${summary}")
    yield (ExitCode.Success)

  private def getFileSummary(
      errors: List[String],
      entries: List[LogEntry],
      path: Path
  ): IO[Summary] =
    val fileSummary = Summary(
      entries.groupBy(_.level).view.mapValues(_.size).toMap,
      entries.groupBy(_.source).view.mapValues(_.size).toMap,
      errors
    )

    for
      _ <- IO.println(s"[$path] \nerrors: \n${errors.mkString("\n")}")
      _ <- IO.println(s"summary: \n$fileSummary\n")
    yield (fileSummary)

  private def getLogs(paths: List[String]): IO[List[Path]] = IO.blocking {
    paths.flatMap(x => os.list(os.pwd / x).filter(os.isFile))
  }

  def readStream[F[_]: Sync](f: Path): Resource[F, Generator[String]] =
    Resource.make(Sync[F].blocking(os.read.lines.stream(f)))(_ => Sync[F].unit)

  def getLogEntries(file: Path): IO[(Path, List[String], List[LogEntry])] =
    // Determine strategy/ext first
    val strategy = file.ext.toLowerCase match
      case "txt"  => Right(LogEntry.fromString)
      case "json" => Right(LogEntry.fromJson)
      case _      => Left(s"Invalid extension: ${file.ext}")

    strategy match
      case Left(error) =>
        IO.pure((file, List(error), Nil))
      case Right(parseFunc) =>
        // File is valid
        readStream[IO](file).use { gen =>
          val parsedString: Generator[Either[String, LogEntry]] =
            gen.zipWithIndex.filter((line, _) => line.nonEmpty).map((line, _) =>
              parseFunc(line.trim)
            )
          val (errors, entries) =
            parsedString.foldLeft(List.empty[String], List.empty[LogEntry]) {
              case ((errorList, entryList), curr) => curr match
                  case Left(err)    => (err :: errorList, entryList)
                  case Right(entry) => (errorList, entry :: entryList)

            }

          IO.pure((file, errors.reverse, entries.reverse))
        }
