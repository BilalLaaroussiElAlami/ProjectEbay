package AuctionSystem

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

case class NotifySale(reply : ActorRef[Message]) extends Message
case class AcknowledgementTimeout() extends Message
case class SaleAcknowledged() extends Message
case class SaleConcluded(bidder:ActorRef[Message], toPay:Int) extends Message

object BankActor:
  var sellers:List[ActorRef[Message]] = List()
  var bidders:List[(ActorRef[Message], String, Iban)] = List()
  def apply():Behavior[Message] = Behaviors.receive{(context,message) =>
    message match
      case RegisterBidder(bidder,name,iban) => bidders = (bidder,name,iban) :: bidders
      case SimpleMessage("printbidders")  => context.log.info("i am bank: " + context.self +    " bidders" + bidders)
      case VerifyBidder(name,toPay, auctionActor, sellerActor, bidderActor) =>
        val is_verified =  checkBidderVerified(name:String,toPay:Int)
        if is_verified then
          auctionActor ! BidderVerified()
          context.spawnAnonymous(new Saga().create(sellerActor,bidderActor,context.self,toPay))
        else
          auctionActor ! BidderNotVerified()
      case SaleConcluded(bidder,toPay) =>
        context.log.info(" ✅ sale concluded ✅")
        //Only now when the sale is concluded we decrease the bidders money
        bidders.find(_._1 == bidder).get._3.balance -= toPay
        context.log.info( bidders.find(_._1 == bidder).get._2 + " bought the item, new balance 📉 is " +  bidders.find(_._1 == bidder).get._3.balance)
      case AcknowledgementTimeout() => context.log.info("SALE FAILED")
    Behaviors.same
  }

  def checkBidderVerified(name: String, toPay: Int): Boolean =
    val bidder = bidders.find(b => b._2 == name)
    (bidder.nonEmpty && bidder.get._3.balance >= toPay)


//This actor will send that the item will be sold and will receive acknowledgement
//Business handshake pattern
class Saga:
  def create(seller: ActorRef[Message], bidder: ActorRef[Message], bank:ActorRef[Message], toPay:Int): Behavior[Message] =
    notifySeller(seller, bidder,bank, toPay)

  def notifySeller(seller: ActorRef[Message], bidder: ActorRef[Message], bank:ActorRef[Message], toPay:Int): Behavior[Message] =
    Behaviors.setup { context =>
      context.log.info(s"Sending notification to the seller")
      seller ! NotifySale(context.self)
      context.setReceiveTimeout( scala.concurrent.duration.FiniteDuration(5, "seconds") , AcknowledgementTimeout())
      Behaviors.receiveMessage { message =>
        message match
          case SaleAcknowledged() =>
            context.log.info("Sale acknowledged by seller ✅")
            notifyBidder(bidder, bank,toPay);
          case AcknowledgementTimeout() =>
            context.log.info("Sale not acknowledged by seller because of time out ⌛️ " )
            bank ! AcknowledgementTimeout()
            Behaviors.stopped
    }
  }

  def notifyBidder(bidder: ActorRef[Message], bank:ActorRef[Message], toPay:Int):
  Behavior[Message] = Behaviors.setup { context =>
    context.log.info(s"Sending notification to bidder")
    bidder ! NotifySale(context.self)
    context.setReceiveTimeout( scala.concurrent.duration.FiniteDuration(5, "seconds") , AcknowledgementTimeout())
    Behaviors.receiveMessage { message =>
      message match
        case SaleAcknowledged() =>
          context.log.info(s"Sale acknowledged also by bidder ✅")
          bank ! SaleConcluded(bidder, toPay)
        case AcknowledgementTimeout() =>
          context.log.info("Sale not acknowledged by seller because of time out ⌛️ " )
          bank ! AcknowledgementTimeout()
      Behaviors.stopped
    }
  }
/*
object FirstPartContract:
  def apply(): Behavior[Contract] = Behaviors.receive { (context, message) =>
    message match
      case SignContract(signature, replyTo) =>
        context.log.info("Contract received in the 1st part")

        if signature.nonEmpty then
          context.log.info("Valid signature")
          replyTo ! ContractSigned
        else
          context.log.error("Invalid signature")
          replyTo ! CannotSignContract("The signature is empty")
    Behaviors.same
  }

object SecondPartContract:
  def apply(): Behavior[Contract] = Behaviors.receive { (context, message) =>
    message match
      case SignContract(signature, replyTo) =>
        context.log.info("Contract received in the 2nd part")

        if signature.nonEmpty then
          context.log.info("Valid signature")
          replyTo ! ContractSigned
        else
          context.log.error("Invalid signature")
          replyTo ! CannotSignContract("The signature is empty")
    Behaviors.same
  }*/
