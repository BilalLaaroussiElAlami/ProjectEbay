package solutions

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}

import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration.Duration


case class Client(name: String, address: String)

// Messages
trait KindBuy
case object Furniture extends KindBuy
case object Electronics extends KindBuy

case class Product(name: String, price: Double = 1E-2, kind: KindBuy)

case class Buy(product: Product, replyTo: ActorRef[KindBuy]) extends KindBuy
case class BuyFurniture(product: Product, replyTo: ActorRef[KindBuy]) extends KindBuy
case class BuyElectronics(product: Product, replyTo: ActorRef[KindBuy]) extends KindBuy

case class ProductBought(message: String, code: UUID) extends KindBuy

// Actors
object ElectronicsStore:
  def apply(): Behavior[KindBuy] = Behaviors.receive { (context, message) =>
    context.log.info("Welcome to the ElectronicsStore store ...")

    message match
      case Buy(product, clientRef) =>
        context.log.info(s"The product ${product.name} at the price ${product.price} has been bought.")
        context.log.info(s"Sending the confirmation directly to the client ${clientRef.path.name}")

        clientRef ! ProductBought(s"$product successfully bought!", UUID.randomUUID())

    Behaviors.same
  }

object FurnitureStore:
  def apply(): Behavior[KindBuy] = Behaviors.receive { (context, message) =>
    context.log.info("Welcome to the FurnitureStore store ...")

    message match
      case Buy(product, clientRef) =>
        context.log.info(s"The product ${product.name} at the price ${product.price} has been bought.")
        context.log.info(s"Sending the confirmation directly to the client ${clientRef.path.name}")

        clientRef ! ProductBought(s"Product $product successfully bought!", UUID.randomUUID())
    Behaviors.same
  }

object EStore:
  def apply(): Behavior[KindBuy] = Behaviors.setup { context =>
    val furnitureStore = context.spawn(FurnitureStore(), name="FurnitureStore")
    val electronicsStore = context.spawn(ElectronicsStore(), name="ElectronicsStore")

    Behaviors.receiveMessage { message =>
      message match
        case BuyFurniture(product, replyTo) =>
          context.log.info(s"A furniture buy has been request for the following product $product")
          context.log.info("Sending the information to the corresponding store ...")

          furnitureStore ! Buy(product, replyTo)

        case BuyElectronics(product, replyTo) =>
          context.log.info(s"An electronics has been request for the following product $product")
          context.log.info("Sending the information to the corresponding store ...")

          electronicsStore ! Buy(product, replyTo)

      Behaviors.same
    }
}

object ClientActor:
  def apply(client: Client, eStoreLocation: ActorRef[KindBuy], products: List[Product]): Behavior[KindBuy] =
    Behaviors.setup { context =>
      products.foreach(product => {
        product.kind match {
          case Furniture => eStoreLocation ! BuyFurniture(product, context.self)
          case Electronics => eStoreLocation ! BuyElectronics(product, context.self)
        }
      })

      Behaviors.receiveMessage { message =>
        message match
          case ProductBought(message, code) =>
            context.log.info(s"I, ${client.name} with address ${client.address}, have received the following message from a buy:")
            context.log.info(s"$message with the tracking code $code.")
        Behaviors.same
    }
}

object ShoppingSystem:
  def apply(): Behavior[KindBuy] = Behaviors.setup { context =>
    // Products
    val product1: Product = Product("sofa", 599.99, Furniture)
    val product2: Product = Product("chair", 19.99, Furniture)
    val product3: Product = Product("table", 49.99, Furniture)
    val product4: Product = Product("monitor", 249.99, Electronics)
    val product5: Product = Product("laptop", 1099.99, Electronics)

    // Lists of Products
    val list1: List[Product] = List(product1, product3, product5)
    val list2: List[Product] = List(product2, product4)

    // Clients
    val client1: Client = Client("Sheldon Cooper", "5th Avenue 89")
    val client2: Client = Client("Amy F. Fowler", "Royal Street 56")

    // Connections
    val eStore = context.spawn(EStore(), name = "EStore")

    context.spawn(ClientActor(client1, eStore, list1), name = "Sheldon")
    context.spawn(ClientActor(client2, eStore, list2), name = "Amy")

    // Indicate the system to terminate
    context.system.terminate()

    Behaviors.same
  }

// Application
object ForwardFlow extends App:
  val system: ActorSystem[KindBuy] = ActorSystem(ShoppingSystem(), "ForwardFlow")
  Await.ready(system.whenTerminated, Duration.Inf)
