package AuctionSystem

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

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
