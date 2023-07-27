package AuctionSystem

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

case class GetAuctionInfo(reply:ActorRef[Message])  extends Message
case class AllAuctions(content: List[AuctionReply]) extends Message

object EbayActor:
  var auctionActors: List[ActorRef[Message]] = List()
  var bidders:List[ActorRef[Message]] = List()
  def apply(): Behavior[Message] = Behaviors.receive{(context,message) =>
    message match
      case RegisterAuction(auctionActor)  => auctionActors = auctionActor::auctionActors
      case RegisterBidder(bidder)         => bidders = bidder::bidders
      case GetAuctions(bidder)            => context.spawnAnonymous(new AuctionsGetter().create(auctionActors, bidder))
      case SimpleMessage("printauctions") => context.log.info("auctions: " + auctionActors)
      case SimpleMessage("printbidders")  => context.log.info("i am ebay: " + context.self +    " bidders" + bidders)
    Behaviors.same
  }


class AuctionsGetter:
  var auctionActors: List[ActorRef[Message]] = List()
  var results:List[AuctionReply] = List()
  var bidder:ActorRef[Message] = null
  def create(auctionActors: List[ActorRef[Message]], bidder: ActorRef[Message]):Behavior[Message] = Behaviors.setup{context =>
    this.auctionActors = auctionActors
    this.bidder = bidder
    auctionActors.foreach(a => a ! GetAuctionInfo(context.self))

    Behaviors.receiveMessage{message =>
      message match
        case a: AuctionReply =>
          results = a :: results
          if(results.length == auctionActors.length)
            bidder ! AllAuctions(results)
            Behaviors.stopped  //When the results have been gathered and sent this process has fulfilled its purpose and thus should stop.
          else Behaviors.same
    }
  }



