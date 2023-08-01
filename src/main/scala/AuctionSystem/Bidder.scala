package AuctionSystem

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

case class MakeBid(name:String, offer:Int, bidderName:String = null, iban: Iban = null) extends Message
case class ReturnItem(itemName:String, buyerName:String = null) extends Message

class Bidder:
  var ebay:ActorRef[Message] = null
  var bank:ActorRef[Message] = null
  var name:String = null
  var iban:Iban = null
  var bids: List[Bid] = List() //A list of bids the bidder has done
  var liveAuctions: AllAuctions = AllAuctions(List[AuctionReply]())
  var OwnedItems = List[(String, Boolean, ActorRef[Message])]()  //(ame_item, is_returnable, seller)
  def create(name:String, iban: Iban,ebay:ActorRef[Message],bank:ActorRef[Message]):Behavior[Message] = Behaviors.setup { context =>
    this.ebay = ebay
    this.bank = bank
    this.name = name
    this.iban = iban
    bank ! RegisterBidder(context.self, this.name, this.iban)
    ebay ! RegisterBidder(context.self, this.name, this.iban)
    bidderListener(context.self)
  }
  def bidderListener(context:ActorRef[Message]):Behavior[Message]  = Behaviors.receive{(context,message) =>
    message match
      case MakeBid(itemName, offer, _, _)  =>
        val auction = liveAuctions.getAuctionActorWithItemName(itemName)
        if(auction.nonEmpty)
          val bid = Bid(offer, context.self, auction.get.auctionActor, name,iban);
          this.bids = bid :: bids;
          bid.auction ! bid
      //A bidder can get SurpassedBid for his own bid
      case SurpassedBid(bid) => context.log.info(name + " knows there is a new highest bid \uD83D\uDD1D: " + bid)
      case liveAuctions: AllAuctions => this.liveAuctions = liveAuctions
      case g: GetAuctions => ebay ! GetAuctions(context.self)
      case SaleConcluded(bidder, seller, nameItem, toPay) =>
        context.log.info(name + " added ➕ item: " + nameItem)
        OwnedItems =  (nameItem, true, seller) :: OwnedItems
      case NotifySale(reply) => reply ! SaleAcknowledged()
      case ReturnItem(itemName,_) =>
        context.log.info(name + " wants to return " + itemName)
        val Seller = OwnedItems.find(_._1 == itemName).get._3
        OwnedItems = OwnedItems.filterNot(_._1 == itemName)
        Seller ! ReturnItem(itemName, name)
        //TODO contact bank again as well to reimburse
      case AuctionReopened(a) =>
        context.log.info(name  + " knows that auction is reopened 🔄")

      case SimpleMessage("printselfAndDependends") => context.log.info("i am bidder " + context.self.toString + " ebay " + ebay + " bank " + bank)
      case SimpleMessage("print auctions") => context.log.info("I am bidder " + context.self.toString +" \nauctions are: " + liveAuctions)
    Behaviors.same
  }