package AuctionSystem

import akka.actor.typed
import akka.actor.typed.scaladsl.{Behaviors, LoggerOps}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, scaladsl}

import scala.collection.mutable.{ArrayStack, ListBuffer}



case class AuctionData(itemName: String, price:Double, time:Double = 10, bids:List[Double] = List()) 
trait Message
case class SimpleMessage(content:String) extends Message
case class CreateAuction(auction: AuctionData, ebayActor: ActorRef[Message]) extends Message
case class RegisterAuction(auctionRef: ActorRef[Message]) extends Message  //when registering an auction at ebay for example
case class RegisterBidder(bidderRef: ActorRef[Message]) extends Message    //when registering a bidder at the bank
case class Bid(price:Int, bidder:ActorRef[Message], auction: ActorRef[Message]) extends Message

class SellerActor:
  var auctions:ListBuffer[ActorRef[Message]] = ListBuffer()
  def create(): Behavior[Message] = Behaviors.receive{(context, message) =>
    message match
      case CreateAuction(auctiondata,ebay) =>
        val auctionActorRef: ActorRef[Message] = context.spawnAnonymous(new AuctionActor().create(Auctiondata = auctiondata, Ebay = ebay,Seller = context.self))
        auctions += auctionActorRef
      case SimpleMessage("forward") =>
        context.log.info("received forward, i am " + context.self.path)
        auctions.foreach(_ ! SimpleMessage("printAuctionData"))

    Behaviors.same
  }

class AuctionActor:
  var auctiondata:AuctionData = null
  var seller:ActorRef[Message] = null
  var ebay:ActorRef[Message]  = null
  var bids: List[Bid] = List()
  def create(Auctiondata: AuctionData, Ebay: ActorRef[Message],  Seller: ActorRef[Message]):Behavior[Message] = Behaviors.setup{ context =>
    this.auctiondata = Auctiondata
    this.seller = Seller
    this.ebay = Ebay
    ebay !  RegisterAuction(context.self)
    auctionListener(context.self)
  }

  def auctionListener(context:ActorRef[Message]):Behavior[Message] = Behaviors.receive{ (context, message) =>
    message match
      case bid:Bid => this.bids = bid :: bids
      case SimpleMessage("printAuctionData") =>
        context.log.info(auctiondata.toString + " " + "from " + context.self + " seller is "  + this.seller.path)
      case SimpleMessage(msg) => context.log.info(s"AuctionActor received: $msg")
    Behaviors.same
  }

case class Iban(numbers:String)
class Bidder:
  var ebay:ActorRef[Message] = null
  var bank:ActorRef[Message] = null
  var name:String = null
  var iban:Iban = null
  var bids: List[Bid] = List()
  def create(name:String, iban: Iban,ebay:ActorRef[Message],bank:ActorRef[Message]):Behavior[Message] = Behaviors.setup { context =>
    this.ebay = ebay
    this.bank = bank
    this.name = name
    this.iban = iban
    bank ! RegisterBidder(context.self)
    ebay ! RegisterBidder(context.self)
    bidderListener(context.self)
  }
   def bidderListener(context:ActorRef[Message]):Behavior[Message]  = Behaviors.receive{(context,message) =>
     message match
       case bid:Bid => this.bids = bid :: bids
       case SimpleMessage("printselfAndDependends") => context.log.info("i am bidder " + context.self.toString + " ebay " + ebay + " bank " + bank)

     Behaviors.same
   }


object BankActor:
  var sellers:List[ActorRef[Message]] = List()
  var bidders:List[ActorRef[Message]] = List()
  def apply():Behavior[Message] = Behaviors.receive{(context,message) =>
    message match
      case RegisterBidder(bidder) => bidders = bidder :: bidders
      case SimpleMessage("printbidders")  => context.log.info("i am bank: " + context.self +    " bidders" + bidders)
    Behaviors.same
  }

object EbayActor:
  var auctionActors: List[ActorRef[Message]] = List()
  var bidders:List[ActorRef[Message]] = List()
  def apply(): Behavior[Message] = Behaviors.receive{(context,message) =>
    message match
      case RegisterAuction(auctionActor)  => auctionActors = auctionActor::auctionActors
      case RegisterBidder(bidder)         => bidders = bidder::bidders
      case SimpleMessage("printauctions") => context.log.info("auctions: " + auctionActors)
      case SimpleMessage("printbidders")  => context.log.info("i am ebay: " + context.self +    " bidders" + bidders)
    Behaviors.same
  }


//THIS TEST MAKES SELLERS SELLERS AUCTION AUCTIONS
//TESTS THAT WE CAN MAKE MULTIPLE SELLERS THAT HAVE MULTIPLE AUCTIONS
object SellersAuctionsTest:
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

//Tests that bidder is registered at bank and ebay and vice versa
object TestBidder:
  def apply():Behavior[Message] = Behaviors.setup{ context =>
    val ebay = context.spawnAnonymous(EbayActor())
    val bank = context.spawnAnonymous(BankActor())
    val bidder = context.spawnAnonymous(new Bidder().create("Vanderbilt", Iban("BE123"), ebay, bank))
    Thread.sleep(1000) //give some time for the registering to happen
    bidder ! SimpleMessage("printselfAndDependends")
    ebay ! SimpleMessage("printbidders")
    bank ! SimpleMessage("printbidders")
    context.system.terminate()
    Behaviors.same
  }


object Main extends App{
 // val testSellersAndAuctions: ActorSystem[Message] = ActorSystem(SellersAuctionsTest(), "AuctionSystem")
  val testBidder:ActorSystem[Message] = ActorSystem(TestBidder(), "testbidder")
}
