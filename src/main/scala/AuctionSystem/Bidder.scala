package AuctionSystem

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

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
      case bid:Bid => this.bids = bid :: bids;  bid.auction ! bid
      case SurpassedBid(bid) => ???
      case SimpleMessage("printselfAndDependends") => context.log.info("i am bidder " + context.self.toString + " ebay " + ebay + " bank " + bank)

    Behaviors.same
  }