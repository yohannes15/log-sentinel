import cats.syntax.all.*
import cats.effect.{IO, IOApp, Sync, Async}
import cats.effect.ExitCode

object LogSentinel extends IOApp:
  /** LogSentinel parses text and json files representing logs, and analyzes and
    * summarizes the data. If args is left empty, the program assumes the logs
    * are in the `logs` directory. If args are provided, it will look for logs
    * in the directories specified.
    *
    * @param args
    *   -> Lists of extra directories to look for logs. Make sure directory is
    *   in scope for the program and use correct dir syntax e.g args =
    *   List("logs", "somefolder/logs")
    * @return
    */
  override def run(args: List[String] = List("logs")): IO[ExitCode] = ???
