package AuctionSystem

import akka.actor.typed.scaladsl.{Behaviors, LoggerOps}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}

import scala.collection.mutable.ListBuffer



case class AuctionData(itemName: String, price:Double, time:Double = 10, bids:List[Double] = List()) 
trait Message
case class SimpleMessage(content:String) extends Message
case class CreateAuction(auction: AuctionData, ebayActor: ActorRef[Message]) extends Message
case class LinkAuction(auctionRef: ActorRef[Message]) extends Message

class SellerActor:
  var auctions:ListBuffer[ActorRef[Message]] = ListBuffer()
  def create(): Behavior[Message] = Behaviors.receive{(context, message) =>
    message match
      case CreateAuction(auctiondata,ebay) =>
        val auctionActorRef: ActorRef[Message] = context.spawnAnonymous(new AuctionarActor().create(Auctiondata = auctiondata, Ebay = ebay,Seller = context.self))
        auctions += auctionActorRef
      case SimpleMessage("forward") =>
        context.log.info("received forward, i am " + context.self.path)
        auctions.foreach(_ ! SimpleMessage("printAuctionData"))

    Behaviors.same
  }


class AuctionarActor:
  var auctiondata:AuctionData = null
  var seller:ActorRef[Message] = null
  var ebay:ActorRef[Message]  = null
  var bids: Map[ActorRef[Message], Double] = Map()
  def create(Auctiondata: AuctionData, Ebay: ActorRef[Message],  Seller: ActorRef[Message]):Behavior[Message] = Behaviors.setup{ context =>
    this.auctiondata = Auctiondata
    this.seller = Seller
    this.ebay = Ebay
    ebay !  LinkAuction(context.self)
    auctionListener(context.self)
  }

  def auctionListener(context:ActorRef[Message]):Behavior[Message] = Behaviors.receive{ (context, message) =>
    message match
      case SimpleMessage("printAuctionData") =>
        context.log.info(auctiondata.toString + " " + "from " + context.self + " seller is "  + this.seller.path)
      case SimpleMessage(msg) => context.log.info(s"AuctionActor received: $msg")
    Behaviors.same
  }

object EbayActor:
  var auctionActors: List[ActorRef[Message]] = List()
  def apply(): Behavior[Message] = Behaviors.receive{(context,message) =>
    message match
      case LinkAuction(auctionActor) =>
        auctionActors = auctionActor::auctionActors
      case SimpleMessage("printauctions") =>
        context.log.info("auctions: " + auctionActors)
    Behaviors.same

  }

object System:
  def apply(): Behavior[Message] = Behaviors.setup { context =>
    // Connections
    val ebay = context.spawnAnonymous(EbayActor())
    val Firstseller = context.spawnAnonymous(new SellerActor().create())
    Firstseller ! CreateAuction(AuctionData("vase", 20, 100), ebay)
    Firstseller ! SimpleMessage("forward")

    val secondSeller = context.spawnAnonymous(new SellerActor().create())
    secondSeller ! CreateAuction(AuctionData("headphone", 10), ebay)
    secondSeller ! SimpleMessage("forward")

    Thread.sleep(2000)
    ebay ! SimpleMessage("printauctions")

    // Indicate the system to terminate
    context.system.terminate()
    Behaviors.same
  }

object Main extends App{
  val system: ActorSystem[Message] = ActorSystem(System(), "AuctionSystem")
}
