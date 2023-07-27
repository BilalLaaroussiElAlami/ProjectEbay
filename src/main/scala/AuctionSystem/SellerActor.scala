package AuctionSystem

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

import scala.collection.mutable.ListBuffer

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