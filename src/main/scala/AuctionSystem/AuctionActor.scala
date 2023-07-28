package AuctionSystem

import akka.actor.typed.scaladsl.ActorContext //THIS WAD JAVADSL
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.{Actor, ActorLogging, Timers}

import java.time.Duration.*
import scala.concurrent.duration.*

case class SurpassedBid(bid: Bid) extends Message
class AuctionActor:
  case class AuctionEnded() extends Message
  var auctiondata:AuctionData = null  //only one
  var acceptBids = true
  var seller:ActorRef[Message] = null //only one
  var ebay:ActorRef[Message]  = null
  var bids: List[Bid] = List()
  def create(Auctiondata: AuctionData, Ebay: ActorRef[Message],  Seller: ActorRef[Message]):Behavior[Message] = Behaviors.setup{ context =>
    this.auctiondata = Auctiondata
    this.seller = Seller
    this.ebay = Ebay
    ebay !  RegisterAuction(context.self)
    context.scheduleOnce( scala.concurrent.duration.FiniteDuration(auctiondata.time,"seconds") , context.self, AuctionEnded())
    receive(context.self)
  }

  def receive (context:ActorRef[Message]):Behavior[Message] = Behaviors.receive{ (context, message) =>
    message match
      case AuctionEnded() => acceptBids = false
      case bid:Bid  => if acceptBids then processBid(context, bid) else context.log.info("times up 😩 🔔 bid failed " + bid)
      case GetAuctionInfo(sender) => sender ! AuctionReply(context.self, auctiondata.itemName, bids.map(_.price).maxOption.getOrElse(auctiondata.price).asInstanceOf[Double])
      case SimpleMessage("printAuctionData") =>
        context.log.info(auctiondata.toString + " " + "from " + context.self + " seller is "  + this.seller.path)
      case SimpleMessage(msg) => context.log.info(s"AuctionActor received: $msg")
    Behaviors.same
  }

  def processBid(context:ActorContext[Message],  bid:Bid) =
    //context.log.info("processing bid " + bid)
    if(bid.price > bids.map(b => b.price).maxOption.getOrElse(0))
      (bid ::  bids).foreach(b => b.bidder ! SurpassedBid(bid))

    this.bids = bid :: bids;














