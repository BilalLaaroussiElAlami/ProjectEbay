package AuctionSystem

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

case class NotifySale(reply : ActorRef[Message]) extends Message
case class AcknowledgementTimeout() extends Message
case class SaleAcknowledged() extends Message
case class SaleConcluded(bidder:ActorRef[Message], seller: ActorRef[Message], nameItem:String, toPay:Int) extends Message
case class Reimburse(BuyerName:String) extends Message

object BankActor:
  var sellers:List[ActorRef[Message]] = List()
  var bidders:List[(ActorRef[Message], String, Iban)] = List()
  def apply():Behavior[Message] = Behaviors.receive{(context,message) =>
    message match
      case RegisterBidder(bidder,name,iban) => bidders = (bidder,name,iban) :: bidders
      case SimpleMessage("printbidders")  => context.log.info("i am bank: " + context.self +    " bidders" + bidders)
      case VerifyBidder(name,toPay, auctionActor, sellerActor, bidderActor, itemName:String) =>
        val is_verified =  checkBidderVerified(name:String,toPay:Int)
        if is_verified then
          auctionActor ! BidderVerified()
          context.spawnAnonymous(new Saga().create(sellerActor,bidderActor,context.self,toPay, itemName))
        else
          auctionActor ! BidderNotVerified()
      case SaleConcluded(bidder, seller, nameItem, toPay) =>  //Sale is acknowledged by both parties
        context.log.info(" ✅ sale concluded ✅")
        //Only now when the sale is concluded we decrease the bidders money
        bidders.find(_._1 == bidder).get._3.balance -= toPay
        //We will tell the bidder to add the item to his inventory of bought items
        bidder ! SaleConcluded(bidder, seller, nameItem, toPay)
        seller ! SaleConcluded(bidder, seller, nameItem, toPay)
        context.log.info( bidders.find(_._1 == bidder).get._2 + " bought the item, new balance 📉 is " +  bidders.find(_._1 == bidder).get._3.balance)
      case Reimburse(name) =>
        context.log.info("reimbursed " + name)
      case AcknowledgementTimeout() => context.log.info("SALE FAILED")
    Behaviors.same
  }

  def checkBidderVerified(name: String, toPay: Int): Boolean =
    val bidder = bidders.find(b => b._2 == name)
    (bidder.nonEmpty && bidder.get._3.balance >= toPay)


//This actor will send that the item will be sold and will receive acknowledgement
//Business handshake pattern
class Saga:
  def create(seller: ActorRef[Message], bidder: ActorRef[Message], bank:ActorRef[Message], toPay:Int, itemName:String): Behavior[Message] =
    notifySeller(seller, bidder,bank, toPay, itemName)

  def notifySeller(seller: ActorRef[Message], bidder: ActorRef[Message], bank:ActorRef[Message], toPay:Int, itemName:String): Behavior[Message] =
    Behaviors.setup { context =>
      context.log.info(s"Sending notification to the seller")
      seller ! NotifySale(context.self)
      context.setReceiveTimeout( scala.concurrent.duration.FiniteDuration(5, "seconds") , AcknowledgementTimeout())
      Behaviors.receiveMessage { message =>
        message match
          case SaleAcknowledged() =>
            context.log.info("Sale acknowledged by seller ✅")
            notifyBidder(bank, bidder,seller, itemName, toPay);
          case AcknowledgementTimeout() =>
            context.log.info("Sale not acknowledged by seller because of time out ⌛️ " )
            bank ! AcknowledgementTimeout()
            Behaviors.stopped
    }
  }

  def notifyBidder(bank:ActorRef[Message], bidder: ActorRef[Message], seller: ActorRef[Message], itemName:String, toPay:Int ):
  Behavior[Message] = Behaviors.setup { context =>
    context.log.info(s"Sending notification to bidder")
    bidder ! NotifySale(context.self)
    context.setReceiveTimeout( scala.concurrent.duration.FiniteDuration(5, "seconds") , AcknowledgementTimeout())
    Behaviors.receiveMessage { message =>
      message match
        case SaleAcknowledged() =>
          context.log.info(s"Sale acknowledged also by bidder ✅")
          bank ! SaleConcluded(bidder, seller, itemName, toPay)
        case AcknowledgementTimeout() =>
          context.log.info("Sale not acknowledged by seller because of time out ⌛️ " )
          bank ! AcknowledgementTimeout()
      Behaviors.stopped
    }
  }

