package AuctionSystem

import akka.actor.typed
import akka.actor.typed.scaladsl.{Behaviors, LoggerOps}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, scaladsl}
import AuctionSystem._

trait Message

case class AuctionData(itemName: String, price:Double, time: Int = 10)
case class AuctionReply(auctionActor: ActorRef[Message], itemName:String, HighestBid:Double) extends Message //an auction will send an auctionreply to biddere
case class GetAuctions(bidder:ActorRef[Message] = null) extends Message //ebay will receive these messages from bidders
case class GetAuctionInfo(sender:ActorRef[Message])  extends Message //sent to bidder, an AuctionReply should be sent to sender
case class AllAuctions(content: List[AuctionReply]) extends Message:  //a list of auctions will be gathered in ebay, wrapped in AllAuctions and sent to some bidder
  def getAuctionActorWithItemName(name:String) = content.find(a => a.itemName == name)

case class SimpleMessage(content:String) extends Message
case class CreateAuction(auction: AuctionData, ebayActor: ActorRef[Message]) extends Message
case class RegisterAuction(auctionRef: ActorRef[Message]) extends Message  //when registering an auction at ebay for example
//when registering a bidder at the bank
//Ebay wont need name nor string so default values is null, but bank does need that
case class RegisterBidder(bidderRef: ActorRef[Message], name:String = null, iban:Iban = null) extends Message
case class Bid(price:Int, bidder:ActorRef[Message], auction: ActorRef[Message], namebidder:String, iban:Iban) extends Message
case class Iban(numbers:String, var balance:Int)


//TESTS THAT WE CAN MAKE MULTIPLE SELLERS THAT HAVE MULTIPLE AUCTIONS
object TestSellersAuctions:
  def apply(): Behavior[Message] = Behaviors.setup { context =>
    // Connections
    val ebay = context.spawnAnonymous(EbayActor())
    val bank = context.spawnAnonymous(BankActor())
    val Firstseller = context.spawnAnonymous(new SellerActor().create(bank))
    Firstseller ! CreateAuction(AuctionData("vase", 20, 100), ebay)
    Firstseller ! SimpleMessage("forward")

    val secondSeller = context.spawnAnonymous(new SellerActor().create(bank))
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
    val bidder = context.spawnAnonymous(new Bidder().create("Vanderbilt", Iban("BE123", 1000000), ebay, bank))
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
    val bidder = context.spawnAnonymous(new Bidder().create("Vanderbilt", Iban("BE123",1000000), ebay, bank))
    val bidder2 = context.spawnAnonymous(new Bidder().create("Rotschild", Iban("FR123",1000000), ebay, bank))
    val Seller = context.spawnAnonymous(new SellerActor().create(bank))
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
    val seller = context.spawnAnonymous(new SellerActor().create(bank))
    seller ! CreateAuction(AuctionData("vase", 20, 100), ebay)
    seller ! CreateAuction(AuctionData("pot",10,100), ebay)
    Thread.sleep(2000) //wait for auctions get registered at ebay
    val bidder = context.spawnAnonymous(new Bidder().create("Vanderbilt", Iban("BE123",1000000), ebay, bank))

    bidder ! GetAuctions()
    Thread.sleep(2000) //wait for bidder to receive auctions
    bidder ! SimpleMessage("print auctions")
    context.system.terminate()
    Behaviors.same
  }

// Tests that bidders can bid on an auction
// When they have the highest bid all bidders of that auction will be informed
// an auction only accepts bids for a certain amount of time
object TestAuctioning:
  def apply():Behavior[Message] = Behaviors.setup{ context =>
    val ebay = context.spawnAnonymous(EbayActor())
    val bank = context.spawnAnonymous(BankActor())
    val FirstBidder = context.spawnAnonymous(new Bidder().create("Vanderbilt", Iban("BE123",1000000), ebay, bank))
    val SecondBidder= context.spawnAnonymous(new Bidder().create("Rotschild", Iban("FR123",1000000), ebay, bank))
    val Seller = context.spawnAnonymous(new SellerActor().create(bank))
    Seller ! CreateAuction(AuctionData("vase", 5, 5), ebay)  //Auction available for 10 seconds
    Thread.sleep(1000) //wait for auctions to be registered at ebay
    FirstBidder ! GetAuctions()
    SecondBidder! GetAuctions()
    Thread.sleep(1000) //wait for bidders to get auctions
    FirstBidder !  MakeBid("vase", 30)  //made a higher bid,  al bidders should be informed -> will be printed on screen
    Thread.sleep(1000) //wait first bid is processed
    SecondBidder! MakeBid("vase", 40)
    Thread.sleep(3000)  //6 seconds have passsed since creating auction the auction's time is 5 seconds, so no bids should be accepted from now on
    SecondBidder ! MakeBid("vase", 50)
    context.system.terminate()
    Behaviors.same
  }

//Tests a bidder that bids but doesnt have enough money, the auction will be rebid
object TestRebid:
  def apply():Behavior[Message] = Behaviors.setup{ context =>
    val ebay = context.spawnAnonymous(EbayActor())
    val bank = context.spawnAnonymous(BankActor())
    val Eric =  context.spawnAnonymous(new Bidder().create("Eric", Iban("BE123",10), ebay, bank)) //Eric has 10 euro
    val Bob = context.spawnAnonymous(new Bidder().create("Bob", Iban("BE123",1000), ebay, bank))

    val Seller = context.spawnAnonymous(new SellerActor().create(bank))
    Seller ! CreateAuction(AuctionData("vase", 10 , 5), ebay)  //the vase costs 10 euro
    Thread.sleep(1000) //wait for auctions to be registered at ebay
    Eric ! GetAuctions()
    Bob ! GetAuctions()
    Thread.sleep(1000) //wait for bidders to get auctions
    Eric !  MakeBid("vase", 30)  //erics bids 30 euro NOT ENOUGH
    Thread.sleep(4000) //wait for the auction to end, the bid should become active again after the auction actor received a  BidderNotVerified message from the bank
    Bob ! MakeBid("vase", 30) //This bid should be accepted
    Behaviors.same
  }


object TestbankAcknowledgeBusinessHanshake:
    def apply(): Behavior[Message] = Behaviors.setup { context =>
      val ebay = context.spawnAnonymous(EbayActor())
      val bank = context.spawnAnonymous(BankActor())
      val FirstBidder = context.spawnAnonymous(new Bidder().create("Vanderbilt", Iban("BE123", 100), ebay, bank))
      val Seller = context.spawnAnonymous(new SellerActor().create(bank))
      Seller ! CreateAuction(AuctionData("vase", 5, 5), ebay) //Auction available for 10 seconds
      Thread.sleep(1000) //wait for auctions to be registered at ebay
      FirstBidder ! GetAuctions()
      Thread.sleep(1000) //wait for bidder to get auctions
      FirstBidder ! MakeBid("vase", 30)
      Behaviors.same
  }


object Main extends App {
  //val testSellersAndAuctions: ActorSystem[Message] = ActorSystem(TestSellersAuctions(), "AuctionSystem")
  //val testBidder: ActorSystem[Message] = ActorSystem(TestBidder(), "testbidder")
  //val testGetAuctions: ActorSystem[Message] = ActorSystem(TestGetAuctions(), "testgetauctions")
  //val testAuctionTime:ActorSystem[Message] = ActorSystem(TestAuctioning(), "testauction")
  //val testRebid:ActorSystem[Message] = ActorSystem(TestRebid(),"testRebid")
  val testBankSaleConclusion :ActorSystem[Message] = ActorSystem(TestbankAcknowledgeBusinessHanshake(),"testRebid")
}
