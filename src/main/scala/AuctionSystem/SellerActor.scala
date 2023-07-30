package AuctionSystem

import akka.actor.{Cancellable, PoisonPill}
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

import scala.collection.mutable.ListBuffer



case class KillAuction(itemName:String) extends Message
case class AuctionInfo(auctionData: AuctionData, AuctionActor: ActorRef[Message], var returnable: Boolean ) extends Message


class SellerActor:
  var auctions = List[AuctionInfo]()
  var bank:ActorRef[Message] = null
  var ebay:ActorRef[Message] = null
  var Owneditems = List[String]() //List of items the seller owns
  var ScheduledAuctionsToKill = List[(String, Cancellable)]()
  val rebidWhenReturned = true

  def create(bank:ActorRef[Message]): Behavior[Message] = Behaviors.setup{context =>
    this.bank = bank
    Behaviors.receive { (context, message) =>
      message match
        case CreateAuction(auctiondata, ebay) =>
          this.ebay = ebay
          val auctionActor: ActorRef[Message] = context.spawnAnonymous(new AuctionActor().create(Auctiondata = auctiondata, Ebay = ebay, Seller = context.self, Bank = bank))
          auctions = AuctionInfo(auctiondata, auctionActor, true)  :: auctions
          Owneditems = auctiondata.itemName :: Owneditems
        case NotifySale(reply: ActorRef[Message]) =>
          reply ! SaleAcknowledged()
        case SimpleMessage("forward") =>
          context.log.info("received forward, i am " + context.self.path)
          auctions.foreach(_.AuctionActor ! SimpleMessage("printAuctionData"))
        case SaleConcluded(bidder, seller, nameItem, toPay) => //this message was sent by bank
          Owneditems = Owneditems.filterNot(_ == nameItem)
          context.log.info("Seller removed item from inventory ❌")
          val ScheduleKillAuction =
            context.scheduleOnce(
              scala.concurrent.duration.FiniteDuration(auctions.find(_._1.itemName == nameItem).get._1.gracePeriod, "seconds"),
              context.self,
              KillAuction(nameItem))
          ScheduledAuctionsToKill = (nameItem, ScheduleKillAuction) :: ScheduledAuctionsToKill

        case KillAuction(itemName) =>
          context.log.info("grace period ended for " + itemName + " ⏰ returning not possible anymore")
          auctions.find(_.auctionData.itemName == itemName).get.returnable = false
          val AuctionActor = getAuctionActorOfItem(itemName)
          //only now we can kill the auction process
          AuctionActor ! Stop()
        case ReturnItem(nameItem, nameBuyer) =>
          //Stop the scheduling of killing the auction
          if auctions.find(_.auctionData.itemName == nameItem).get.returnable then
            auctions.find(_.auctionData.itemName == nameItem).get.returnable = false
            //cancel the scheduling to kill this auction
            ScheduledAuctionsToKill.find(_._1 == nameItem).get._2.cancel()
            ScheduledAuctionsToKill = ScheduledAuctionsToKill.filterNot(_._1 == nameItem)
            Owneditems = nameItem  :: Owneditems
            val AuctionActor = getAuctionActorOfItem(nameItem)
            context.log.info("Grace period not over yet, reauctioning item")
            if rebidWhenReturned then
              AuctionActor ! Rebid()  //Default immediately rebid
              bank ! Reimburse(nameBuyer)
          else context.log.info("bidder tried to return " + nameItem + " but grace period is over, nothing will happen \uD83E\uDD37\u200D\uFE0F")

       Behaviors.same
    }
  }
  def getAuctionActorOfItem(nameItem: String) =
    auctions.find(_._1.itemName == nameItem).getOrElse(throw new RuntimeException(" ⚠️ No AuctionActor associated with the item name"))._2
