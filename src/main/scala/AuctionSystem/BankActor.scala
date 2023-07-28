package AuctionSystem

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object BankActor:
  var sellers:List[ActorRef[Message]] = List()
  var bidders:List[(ActorRef[Message], String, Iban)] = List()
  def apply():Behavior[Message] = Behaviors.receive{(context,message) =>
    message match
      case RegisterBidder(bidder,name,iban) => bidders = (bidder,name,iban) :: bidders
      case SimpleMessage("printbidders")  => context.log.info("i am bank: " + context.self +    " bidders" + bidders)
      case VerifyBidder(name,toPay, reply) =>   context.log.info("bank receiver verifyrequest"); reply ! doVerifyBidder(name,toPay)
    Behaviors.same
  }

  def doVerifyBidder(name:String,toPay:Int):Message =
    val bidder = bidders.find(b => b._2 == name)
    if(bidder.nonEmpty && bidder.get._3.balance >= toPay) BidderVerified() else BidderNotVerified()



