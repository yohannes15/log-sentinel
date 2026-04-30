# Circe

- Json library for Scala
- Core project has only one dependency (cats-core)
- Doesn't include a JSON parser in the core project (focused on JSON AST, zippers and codecs)
- Doesn't use or provide lenses in the core project
- Doesn't use macros or provide any kind of automatic derivation in the core project. Instead includes a subproject (generic) that provides generic codec derivation using Shapeless.

## Quickstart

```sbt
val circleVersion = "0.14.15"
libraryDependencies ++= Seq(
    "io.circe" %% "circe-core",
    "io.circe" %% "circe-generic"
    "io.circe" %% "circe-parser"
).map(_ % circeVersion)
```

In case of large/deep-nested case classes, there is a chance to get stack overflow during compilation, refert [https://circe.io/circe/codecs/known-issues.html](known-issues) for workaround.


```scala
import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._

sealed trait Foo
case class Bar(xs: Vector[String]) extends Foo
case class Qux(i: Int, d: Option[Double]) extends Foo

val foo: Foo = Qux(13, Some(14.0))

val json = foo.asJson.noSpaces
println(json)

val decodedFoo = decode[Foo](json)
println(decodedFoo)
```

## Parsing Json

Circe includes a parsing module, which on the JVM is wrapper around the `Jawn` JSON parser and for JS uses the built-in `JSON.parse`. Parsing isn't part of `circe-core`, but rather `circe-parser`

```scala
import io.circe._, io.circe.parser._

val rawJson: String = """
{
    "foo": "bar",
    "baz": 123,
    "list": [4, 5, 6]
}
"""

val parseResult = parse(rawJson)
```

Because parsing might fail, the result is an `Either` with an `io.circe.Error` on the left side. In the example above, the input was valid JSON, so the result was a `Right` containing the corresponding JSON representation.

```scala
// parsing invalid json
val badJson: String = "yolo"

parse(badJson)
// res0: Either[ParsingFailure, Json] = Left(
//   value = ParsingFailure(
//     message = "expected json value got 'yolo' (line 1, column 1)",
//     underlying = ParseException(
//       msg = "expected json value got 'yolo' (line 1, column 1)",
//       index = 0,
//       line = 1,
//       col = 1
//     )
//   )
// )
```

There are a number of ways to extract the parse result from the `Either`.

```scala
parse(rawJson) match 
    case Left(failure) => ...
    case Right(json) => ...

// getOrElse (provided by Cats)
val json: Json = parse(rawJson).getOrElse(Json.Null)
// json: Json = JObject(
//   value = object[foo -> "bar",baz -> 123,list of stuff -> [
//   4,
//   5,
//   6
// ]]
// )
```

## Cursors (Traversing and modifying JSON)

Working with JSON in circe usually involves using a cursor. Cursors are used both for extracting data and for performing modification.

Suppose we have the following JSON document.

```scala
import cats.syntax.either._
import io.circe._, io.circe.parser._

val json: String = """
  {
    "id": "c730433b-082c-4984-9d66-855c243266f0",
    name": "Foo",
    "counts": [1, 2, 3],
    "values": {
      "bar": true,
      "baz": 100.001,
      "qux": ["a", "b"]
    }

  }
"""

val doc: Json = parse(json).getOrElse(Json.Null)
```

### Extracting data

In order to traverse the document we need to create an `HCursor` with the focus at the document's root and then we can use various operations to move the focus of the cursor around the document and extract data from it.

```scala
val cursor: HCursor = doc.hcursor

val baz: Decoder.Result[Double] = cursor.downField("values").downField("baz").as[Double]
// baz: Decoder.Result[Double] = Right(value = 100.001)

// you can also use `get[A](key)` as shorthand for `downField(key).as[A]`
val baz2: Decoder.Result[Double] = cursor.downField("values").get[Double]("baz")
// baz2: Decoder.Result[Double] = Right(value = 100.001)

val secondQux: Decoder.Result[String] = cursor.downField("values").downField("qux").downArray.as[String]
// secondQux: Decoder.Result[String] = Right(value = "a")
```

### Transforming data

We can also use a cursor to modify JSON. We can then return to the root of the document and return its value with `top`. Note that Json is immutable, so original document is left unchanged.

```scala
val reversedNameCursor: ACursor = cursor.downField("name").withFocus(_.mapString(_.reverse))

val reversedName: Option[Json] = reversedNameCursor.top // as you can see Foo is now ooF
// reversedName: Option[Json] = Some(
//   value = JObject(
//     value = object[id -> "c730433b-082c-4984-9d66-855c243266f0",name -> "ooF",counts -> [
//   1,
//   2,
//   3
// ],values -> {
//   "bar" : true,
//   "baz" : 100.001,
//   "qux" : [
//     "a",
//     "b"
//   ]
// }]
//   )
// )
```

### Cursor Implementations
- Cursor: provides functionality for moving around a tree and making modifications
- HCursor: tracks history of operations performed. This can be used to provide useful error msgs when something goes wrong.
- ACursor: tracks history also but represents the possibility of failure (e.g calling downField on a field that doesn't exist.)
- There are other ways to traverse documents (see Optics)

## Encoding and Decoding (Codecs)

Circe usees `Encoder` and `Decoder` type classes for encoding and decoding. An `Encoder[A]` instance provides a function that will convert any `A` to a `Json`, and a `Decoder[A]` takes a `Json` value to either an exception or an `A`. Circe provides implict instances of these type classes for many types from the Scala std library, including Int, String, and others. It also provides instances for List[A], Option[A], and other generic types, but only if `A` has an `Encoder` instance

Encoding data to `Json` can be done using the `.asJson` syntax
```scala
import io.circe.syntax.*

val intsJson = List(1, 3, 3).asJson
// intsJson: io.circe.Json = JArray(
//   value = Vector(
//     JNumber(value = JsonLong(value = 1L)),
//     JNumber(value = JsonLong(value = 2L)),
//     JNumber(value = JsonLong(value = 3L))
//   )
// )
```

Use the `.as` syntax for decoding data from `Json`
```scala
intsJson.as[List[Int]]
// res0: Either[io.circe.Error, List[Int]] = Right(value = List(1, 2, 3))
```

The `decode` function from the included parser module can be used to directly decode a JSON `String`.

```scala
import io.circe.parser.decode

decode[List[Int]]("[1, 2, 3]")
// res1: Either[io.circe.Error, List[Int]] = Right(value = List(1, 2, 3))
```

### Semi-automatic Derivation

Its convenient to have an `Encoder` and `Decoder` defined in your code, and semi-automatic deriviation helps.

```scala
import io.circe.*
import io.circe.syntax.*
import io.circe.generic.semiauto.*

case class Foo(a: Int, b: String, c: Boolean)
given Decoder[Foo] = deriveDecoder
given Encoder[Foo] = deriveEncoder
Foo(13, "Quix", false).asJson
```

Using derives syntax
```scala
case class Foo(a: Int, b: String, c: Boolean) derives Decoder, Encoder
Foo(13, "Quix", false).asJson
```

#### Specific case for Value Class derivation

Most of the time, when using case class / value classes, we expect only the inner value in the serialized format. It can be achieved using `circe-generic-extras`. For example, below expected serialization for `Foo(123)` is `123`.

```scala

import io.circe.*
import io.circe.generic.extras.semiauto.*

case class Foo(a: Int)

given Decoder[Foo] = deriveUnwrappedDecoder[Foo]
given Encoder[Foo] = deriveUnwrappedEncoder[Foo]
```

#### @JsonCodec (Scala 2 Only)

The `circe-generic` project includes a `@JsonCodec` annotation for Scala 2 that simplifies generic derivation.

**NOTE**: This is **NOT** for Scala 3. In Scala 3, use the `derives` syntax instead. In Scala 2, you need the `-Ymacro-annotations` flag or the `Macro Paradise` plugin.

```scala
import io.circe.generic.JsonCodec
import io.circe.syntax.*

@JsonCodec case class Bar(i: Int, s: String)
Bar(13, "Quix").asJson
// res0: Json = JObject(value = object[i -> 13,s -> "Qux"])
```

#### forProductN helper methods

Its also possible to construct encoders and decoders for case class-like types in a relatively boilerplate-free way without generic derivation

```scala
import io.circe.{Decoder, Encoder}

case class User(id: Long, firstName: String, lastName: String)

given decodeUser: Decoder[User] = Decoder.forProduct3("id", "first_name", "last_name")(User.apply)
// decodeUser: Decoder[User] = io.circe.ProductDecoders$$anon$5@316898a5

given encodeUser: Encoder[User] = Encoder.forProduct3("id", "first_name", "last_name")(u => 
    (u.id, u.firstName, u.lastName)
)
```

Its not as clean or as maintainable as generic derivation, but its less magical, it requires nothing but `circe-core`, and if you need a custom name mapping its currently the best solution (although 0.6.0 introduces experimental configurable generic derivation in the `generic-extras` module)

### Automatic Derivation

It is also possible to derive `Encoders` and `Decoders` for many types with no boilerplate at all. circe uses [https://github.com/milessabin/shapeless](shapeless) (generic programming library for scala) to automatically derive the necessary type class instances:

```scala
import io.circe.generic.auto.*
import io.circe.syntax.*

case class Person(name: String)
case class Greeting(salutation: String, person: Person, exclamationMarks: Int)

Greeting("Hey", Person("Chris"), 3).asJson
// res0: io.circe.Json = JObject(
//   value = object[salutation -> "Hey",person -> {
//   "name" : "Chris"
// },exclamationMarks -> 3]
// )
```

### Custom Codecs 

You can write your own codec, in a couple of ways, instead of using automatic or semi-automatic derivation. Firstly, you can write a new `Encoder[A]` and `Decoder[A]` from scratch:


```scala
import io.circe.{ Decoder, Encoder, HCursor, Json }

class Thing(val foo: String, val bar: Int)

given encodeFoo: Encoder[Thing] = 
  Encoder.instance[Thing] { a =>
    Json.obj(
      ("foo", Json.fromString(a.foo)),
      ("bar", Json.fromInt(a.bar))
    )
  }
// encodeFoo: Encoder[Thing] = io.circe.Encoder$$anon$3@2143934e

given decodeFoo: Decoder[Thing] =
  Decoder.instance[Thing] { c =>
    for {
      foo <- c.downField("foo").as[String]
      bar <- c.get[Int]("bar")
    } yield new Thing(foo, bar)
  }
// decodeFoo: Decoder[Thing] = io.circe.Decoder$$anon$16@46ef64d
```

### Mapping Simple Enums to Strings

When working with simple Scala 3 enums (labels only), using `derives Decoder` can fail if the JSON is a plain string (it expects a tagged object). Instead, map the Decoder to a string.

```scala
enum LogLevel:
  case INFO, WARN, ERROR

object LogLevel:
  given Decoder[LogLevel] = Decoder.decodeString.emap { s =>
    scala.util.Try(LogLevel.valueOf(s.toUpperCase)).toEither
      .leftMap(_ => s"'$s' is not a valid LogLevel")
  }
```

*Note: If you use a manual `given`, remove `derives Decoder` from the enum definition to avoid "Ambiguous given instances" errors.*

### Handling Custom Types (e.g. Timestamp)

If a type like `java.sql.Timestamp` isn't supported out of the box, map it from a `String` using your own parsing logic.

```scala
object LogEntry:
  given Decoder[Timestamp] = Decoder.decodeString.map(toTimeStamp)
```
