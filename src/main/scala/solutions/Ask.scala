package solutions

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}

// Messages
trait Availability

trait GenreAvailability extends Availability
case class Genre(name: Option[String] = None, listBooks: List[Book] = List.empty) extends GenreAvailability
case class GenreInExistence(genre: Genre) extends GenreAvailability
case class GenreNotFound(genre: Genre) extends GenreAvailability

trait BookAvailability extends Availability
case class Book(name: Option[String] = None) extends BookAvailability
case class BookInExistence(book: Book) extends BookAvailability
case class BookNotFound(book: Book) extends BookAvailability

case class SendGenre(genre: Genre, replyTo: ActorRef[Availability]) extends Availability
case class SendBook(book: Book, replyTo: ActorRef[Availability]) extends Availability

case class Request(genre: Genre, book: Book) extends Availability

// Actor for the Requests
object AllRequests:
  def apply(): Behavior[Availability] = Behaviors.setup {context =>
    context.log.info("Making all requests ...")
    val frontPage = context.spawnAnonymous(FrontPage())
    val requests = List(
      Request(Genre(Some("Horror")), Book(None)),
      Request(Genre(None), Book(Some("Book1"))),
      Request(Genre(Some("Science Fiction")), Book(None)),
      Request(Genre(None), Book(Some("Book5"))),
    )

    requests.foreach(request => frontPage ! request)

    Behaviors.same
  }

// Actor for the Front page
object FrontPage:
  def apply(): Behavior[Availability] =
    Behaviors.setup { context =>
      context.log.info("Search requested!")

      val searchActor = context.spawnAnonymous(Search())

      Behaviors.receiveMessage { message =>
        message match
          case Request(genre, book) =>
            context.log.info("The Front Page has received a request")
            if genre.name.nonEmpty then
              searchActor ! SendGenre(genre, context.self)
            if book.name.nonEmpty then
              searchActor ! SendBook(book, context.self)

          case GenreInExistence(genre) => context.log.info(s"Genre in existence: ${genre.name.get}")
          case GenreNotFound(genre) => context.log.info(s"Genre not found: ${genre.name.get}")
          case BookInExistence(book) => context.log.info(s"Book in existence: ${book.name.get}")
          case BookNotFound(book) => context.log.info(s"Book not found: ${book.name.get}")
        Behaviors.same
      }
  }

object Search:
  // Books
  val book1: Book = Book(Some("Book1"))
  val book2: Book = Book(Some("Book 2"))

  val book3: Book = Book(Some("Book3"))
  val book4: Book = Book(Some("Book4"))

  // Genres
  val genre1: Genre = Genre(Some("Science Fiction"), List(book1, book2))
  val genre2: Genre = Genre(Some("Horror"), List(book3, book4))

  // Bookshelf information
  val bookshelf: List[Genre] = List(genre1, genre2)

  def apply(): Behavior[Availability] = Behaviors.receive { (context, message) =>
    message match
      case SendGenre(genre, replyTo) =>
        if searchGenreName(genre.name.get) then
          replyTo ! GenreInExistence(genre)
        else
          replyTo ! GenreNotFound(genre)
      case sendBook: SendBook =>
        val childSearch = context.spawnAnonymous(SearchBook(bookshelf))
        childSearch ! sendBook

      Behaviors.same
  }

  def searchGenreName(genreName: String): Boolean =
    bookshelf.map(_.name.get).contains(genreName)

object SearchBook:
  def apply(bookshelf: List[Genre]): Behavior[Availability] =
    Behaviors.receive { (context, message) =>
      context.log.info("Entering the child actor ...")
      message match
        case SendBook(book, replyTo) =>
          val bookNames: List[String] = bookshelf.flatMap(_.listBooks.map(_.name.get))
          if bookNames.contains(book.name.get) then
            context.log.info(s"Book ${book.name.get} exists!")
            replyTo ! BookInExistence(book)
          else
            context.log.info(s"Book ${book.name.get} does not exist!")
            replyTo ! BookNotFound(book)
        Behaviors.stopped
    }

object Ask extends App:
  val system: ActorSystem[Availability] = ActorSystem(AllRequests(), "Ask")

  system.terminate()
