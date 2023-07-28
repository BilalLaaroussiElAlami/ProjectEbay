package AuctionSystem

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

import scala.collection.mutable.ListBuffer

class SellerActor:
  var auctions:ListBuffer[ActorRef[Message]] = ListBuffer()
  var bank:ActorRef[Message] = null
  def create(bank:ActorRef[Message]): Behavior[Message] = Behaviors.setup{context =>
    this.bank = bank
    Behaviors.receive{(context, message) =>
    message match
      case CreateAuction(auctiondata,ebay) =>
        val auctionActorRef: ActorRef[Message] = context.spawnAnonymous(new AuctionActor().create(Auctiondata = auctiondata, Ebay = ebay,Seller = context.self, Bank = bank))
        auctions += auctionActorRef
      case NotifySale(reply: ActorRef[Message]) =>
        reply ! SaleAcknowledged()
      case SimpleMessage("forward") =>
        context.log.info("received forward, i am " + context.self.path)
        auctions.foreach(_ ! SimpleMessage("printAuctionData"))

    Behaviors.same
  }
  }