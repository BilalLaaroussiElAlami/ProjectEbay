package AuctionSystem

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.{Actor, ActorLogging, Timers, typed}
import solutions.CannotSignContract

import java.time.Duration.*
import scala.concurrent.duration.*

case class SurpassedBid(bid: Bid) extends Message
case class VerifyBidder(name:String, pay:Int, auctionActor: ActorRef[Message], SellerActor:ActorRef[Message], bidderActor:ActorRef[Message], itemName:String) extends Message
case class VerifyBidderTimeout() extends Message
case class BidderVerified() extends Message
case class BidderNotVerified() extends Message
case class Rebid() extends Message
case class AuctionReopened(Sender: ActorRef[Message]) extends Message

class AuctionActor:
  case class AuctionEnded() extends Message
  var auctiondata:AuctionData = null  //only one
  var acceptBids = true
  var seller:ActorRef[Message] = null //only one
  var ebay:ActorRef[Message]  = null
  var bids: List[Bid] = List()  //list of al bids
  //list of all actors that have bid on this auction, this list will not be cleared so that when an item get refunded we can contact the bidders again to tell them
  //the auction is open again
  var bidders:List[ActorRef[Message]] = List()
  var bank: ActorRef[Message] = null
  def create(Auctiondata: AuctionData, Ebay: ActorRef[Message],  Seller: ActorRef[Message], Bank: ActorRef[Message]):Behavior[Message] = Behaviors.setup{ context =>
    this.auctiondata = Auctiondata
    this.seller = Seller
    this.ebay = Ebay
    this.bank = Bank
    ebay !  RegisterAuction(context.self)
    context.scheduleOnce(scala.concurrent.duration.FiniteDuration(auctiondata.time,"seconds") , context.self, AuctionEnded())
    receive(context.self)
  }
  def MaxBidder():Bid =  bids.find(_.price ==  bids.map(_.price).max).get
  def receive (context:ActorRef[Message]):Behavior[Message] = Behaviors.receive{ (context, message) =>
    message match
      case AuctionEnded() =>
        context.log.info(" ⌛️ Auction ended")
        acceptBids = false;
        if(bids.nonEmpty)
          context.log.info("the auction winner 🏆 is " + MaxBidder().namebidder, " he/she offered " + MaxBidder().price);
          context.spawnAnonymous(new contactBank().create(this.bank, context.self, seller,  MaxBidder().namebidder, MaxBidder().price, MaxBidder().bidder, auctiondata.itemName))
      case bid:Bid  =>
        if acceptBids then processBid(context, bid) else context.log.info("times up 😩 🔔 bid failed! " + bid)
      case GetAuctionInfo(sender) =>
        sender ! AuctionReply(context.self, auctiondata.itemName, bids.map(_.price).maxOption.getOrElse(auctiondata.price).asInstanceOf[Double])
      case VerifyBidderTimeout() | BidderNotVerified() =>
        context.log.info("Bidder NOT verified or Timeout") 
        rebid(context)
      case Rebid() =>
        context.log.info("")
        bidders.foreach(_ ! AuctionReopened(context.self))
        rebid(context)
      case BidderVerified() => context.log.info("Auction knows bidder is verified ")
      case Stop() => context.log.info("Auction  process terminated 🔚 "); Behaviors.stopped
      case SimpleMessage("printAuctionData") =>
        context.log.info(auctiondata.toString + " " + "from " + context.self + " seller is " + this.seller.path)
      case SimpleMessage(msg) => context.log.info(s"AuctionActor received: $msg")
    Behaviors.same
  }

  def rebid(context: ActorContext[Message]) =
    context.log.info("rebidding \uD83D\uDD04 !")
    acceptBids = true
    bids = bids.empty
    context.scheduleOnce(scala.concurrent.duration.FiniteDuration(auctiondata.time, "seconds"), context.self, AuctionEnded())

  def processBid(context:ActorContext[Message],  bid:Bid) =
   
    if(bid.price > bids.map(b => b.price).maxOption.getOrElse(0))
      (bid ::  bids).foreach(b => b.bidder ! SurpassedBid(bid))
    bidders = bid.bidder :: bidders
    bids = bid :: bids

class contactBank:
  def create(bankActor: ActorRef[Message], auctionActor: ActorRef[Message],seller:ActorRef[Message], auctionWinnerName:String, toPay:Int, bidderActor: ActorRef[Message], itemName:String) = Behaviors.setup{context =>
    val timeout = 5.seconds
    bankActor ! VerifyBidder(auctionWinnerName, toPay, context.self, seller, bidderActor, itemName)
    context.setReceiveTimeout(timeout, VerifyBidderTimeout())
    Behaviors.receiveMessage{message =>
      message match
        case BidderVerified() =>  auctionActor ! BidderVerified()
        case BidderNotVerified() =>  auctionActor ! BidderNotVerified()
        case VerifyBidderTimeout() =>  auctionActor ! VerifyBidderTimeout()
      Behaviors.stopped
    }
  }















