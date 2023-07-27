package AuctionSystem

import akka.actor.typed
import akka.actor.typed.scaladsl.{Behaviors, LoggerOps}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, scaladsl}
import AuctionSystem._

trait Message

case class AuctionData(itemName: String, price:Double, time: Int = 10)
case class AuctionReply(auctionActor: ActorRef[Message], item:String, HighestBid:Double) extends Message //an auction will send an auctionreply to biddere
case class GetAuctionInfo(sender:ActorRef[Message])  extends Message //sent to bidder, an AuctionReply should be sent to sender
case class AllAuctions(content: List[AuctionReply]) extends Message  //a list of auctions will be gathered in ebay, wrapped in AllAuctions and sent to some bidder


case class SimpleMessage(content:String) extends Message
case class CreateAuction(auction: AuctionData, ebayActor: ActorRef[Message]) extends Message
case class RegisterAuction(auctionRef: ActorRef[Message]) extends Message  //when registering an auction at ebay for example
case class RegisterBidder(bidderRef: ActorRef[Message]) extends Message    //when registering a bidder at the bank
case class Bid(price:Int, bidder:ActorRef[Message], auction: ActorRef[Message]) extends Message
case class Iban(numbers:String)


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

//Tests that when making bidder it is registered at bank and ebay. And that bank and ebay register the bidder
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

//Tests 2 bidders bidding on an auction, they get updated when a bid surpassed, bids after auctiontime are not processed
object TestBid:
  def apply():Behavior[Message] = Behaviors.setup{context =>
    val ebay = context.spawnAnonymous(EbayActor())
    val bank = context.spawnAnonymous(BankActor())
    val bidder = context.spawnAnonymous(new Bidder().create("Vanderbilt", Iban("BE123"), ebay, bank))
    val bidder2 = context.spawnAnonymous(new Bidder().create("Rotschild", Iban("FR123"), ebay, bank))
    val Seller = context.spawnAnonymous(new SellerActor().create())
    Seller ! CreateAuction(AuctionData("MonaLisa", 1000000), ebay)

    Thread.sleep(1000) //give some time for the registering to happen
    context.system.terminate()
    Behaviors.same
  }

//test that a bidder can get auctions from ebay
object TestGetAuctions:
  def apply():Behavior[Message] = Behaviors.setup { context =>
    val ebay = context.spawnAnonymous(EbayActor())
    val bank = context.spawnAnonymous(BankActor())
    val seller = context.spawnAnonymous(new SellerActor().create())
    seller ! CreateAuction(AuctionData("vase", 20, 100), ebay)
    seller ! CreateAuction(AuctionData("pot",10,100), ebay)
    Thread.sleep(2000) //wait for auctions get registered at ebay
    val bidder = context.spawnAnonymous(new Bidder().create("Vanderbilt", Iban("BE123"), ebay, bank))

    bidder ! GetAuctions()
    Thread.sleep(2000) //wait for bidder to receive auctions
    bidder ! SimpleMessage("print auctions")
    context.system.terminate()
    Behaviors.same
  }

object Main extends App {
  //val testSellersAndAuctions: ActorSystem[Message] = ActorSystem(SellersAuctionsTest(), "AuctionSystem")
  //val testBidder: ActorSystem[Message] = ActorSystem(TestBidder(), "testbidder")
  val testGetAuctions: ActorSystem[Message] = ActorSystem(TestGetAuctions(), "testgetauctions")
}
