package AuctionSystem

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors

/*a seller creates an auction multiple bidders bid on it.The auction is over, the buyer returns the item the other bidder bids  on it*/
//we have 1 seller,auction in this scenatrio, but this is just to make it clear, the system does support multiple sellers,bidders,auctions.
object scenario:
  def apply(): Behavior[Message] = Behaviors.setup { context =>
    val ebay = context.spawnAnonymous(EbayActor())
    val bank = context.spawnAnonymous(BankActor())
    val Seller = context.spawnAnonymous(new SellerActor().create(bank))
    val Alice = context.spawnAnonymous(new Bidder().create("Alice", Iban("BE123", 100), ebay, bank))
    val Bob = context.spawnAnonymous(new Bidder().create("Bob", Iban("FR789", 150), ebay, bank))
    Seller ! CreateAuction(AuctionData(itemName = "vase", price = 5, time = 15, gracePeriod = 15), ebay)
    Thread.sleep(1000) //wait for auctions to be registered at ebay
    Alice ! GetAuctions()
    Bob ! GetAuctions()
    Thread.sleep(1000)   //wait for bidders to get auctions
    Alice ! MakeBid("vase", 10)  //Alice bids on vase
    Thread.sleep(3000)   //observe behaviour on terminal
    Bob ! MakeBid("vase", 15)   //Bob makes a higher bid
    Thread.sleep(3000)   //observe behaviour
    Thread.sleep(10000)  //wait until auction is over, observe behaviour
    Bob ! ReturnItem("vase")    //Bob returns the vase, he can because he is still in the grace period, observe behaviour
    Thread.sleep(1000)   //Wait for the item to be returned
    Alice ! MakeBid("vase", 30)
    //Last printed message on terminal is: Auction  process terminated 🔚
    Behaviors.same
  }
object ExecuteScenario extends App {
  val executeScenario :ActorSystem[Message] = ActorSystem(scenario(),"scenario")
}
// OUTPUT
/*
[scenario-akka.actor.default-dispatcher-5] INFO akka.event.slf4j.Slf4jLogger - Slf4jLogger started
  [scenario-akka.actor.default-dispatcher-8] INFO AuctionSystem.Bidder - Alice knows there is a new highest bid 🔝: Bid(10,Actor[akka://scenario/user/$d#2004441520],Actor[akka://scenario/user/$c/$a#-169398937],Alice,Iban(BE123,100))
  [scenario-akka.actor.default-dispatcher-8] INFO AuctionSystem.Bidder - Bob knows there is a new highest bid 🔝: Bid(15,Actor[akka://scenario/user/$e#900217078],Actor[akka://scenario/user/$c/$a#-169398937],Bob,Iban(FR789,150))
  [scenario-akka.actor.default-dispatcher-6] INFO AuctionSystem.Bidder - Alice knows there is a new highest bid 🔝: Bid(15,Actor[akka://scenario/user/$e#900217078],Actor[akka://scenario/user/$c/$a#-169398937],Bob,Iban(FR789,150))
  [scenario-akka.actor.default-dispatcher-8] INFO AuctionSystem.AuctionActor -  ⌛️ Auction ended
  [scenario-akka.actor.default-dispatcher-8] INFO AuctionSystem.AuctionActor - the auction winner 🏆 is Bob
  [scenario-akka.actor.default-dispatcher-3] INFO AuctionSystem.AuctionActor - Auction knows bidder is verified
  [scenario-akka.actor.default-dispatcher-3] INFO AuctionSystem.Saga - Sending notification to the seller
  [scenario-akka.actor.default-dispatcher-3] INFO AuctionSystem.Saga - Sale acknowledged by seller ✅
  [scenario-akka.actor.default-dispatcher-3] INFO AuctionSystem.Saga - Sending notification to bidder
  [scenario-akka.actor.default-dispatcher-3] INFO AuctionSystem.Saga - Sale acknowledged also by bidder ✅
  [scenario-akka.actor.default-dispatcher-8] INFO AuctionSystem.BankActor$ -  ✅ sale concluded ✅
  [scenario-akka.actor.default-dispatcher-3] INFO AuctionSystem.Bidder - Bob added ➕ item: vase
  [scenario-akka.actor.default-dispatcher-6] INFO AuctionSystem.SellerActor - Seller removed item from inventory ❌
  [scenario-akka.actor.default-dispatcher-8] INFO AuctionSystem.BankActor$ - Bob bought the item, new balance 📉 is 135
  [scenario-akka.actor.default-dispatcher-6] INFO AuctionSystem.Bidder - Bob wants to return vase
  [scenario-akka.actor.default-dispatcher-8] INFO AuctionSystem.SellerActor - Grace period not over yet, reauctioning item
  [scenario-akka.actor.default-dispatcher-6] INFO AuctionSystem.AuctionActor -
  [scenario-akka.actor.default-dispatcher-3] INFO AuctionSystem.BankActor$ - reimbursed Bob
  [scenario-akka.actor.default-dispatcher-6] INFO AuctionSystem.AuctionActor - rebidding 🔄 !
[scenario-akka.actor.default-dispatcher-3] INFO AuctionSystem.Bidder - Bob knows that auction is reopened 🔄
  [scenario-akka.actor.default-dispatcher-8] INFO AuctionSystem.Bidder - Alice knows that auction is reopened 🔄
  [scenario-akka.actor.default-dispatcher-5] INFO AuctionSystem.Bidder - Alice knows there is a new highest bid 🔝: Bid(30,Actor[akka://scenario/user/$d#2004441520],Actor[akka://scenario/user/$c/$a#-169398937],Alice,Iban(BE123,100))
  [scenario-akka.actor.default-dispatcher-5] INFO AuctionSystem.AuctionActor -  ⌛️ Auction ended
  [scenario-akka.actor.default-dispatcher-5] INFO AuctionSystem.AuctionActor - the auction winner 🏆 is Alice
  [scenario-akka.actor.default-dispatcher-8] INFO AuctionSystem.AuctionActor - Auction knows bidder is verified
  [scenario-akka.actor.default-dispatcher-8] INFO AuctionSystem.Saga - Sending notification to the seller
  [scenario-akka.actor.default-dispatcher-8] INFO AuctionSystem.Saga - Sale acknowledged by seller ✅
  [scenario-akka.actor.default-dispatcher-8] INFO AuctionSystem.Saga - Sending notification to bidder
  [scenario-akka.actor.default-dispatcher-8] INFO AuctionSystem.Saga - Sale acknowledged also by bidder ✅
  [scenario-akka.actor.default-dispatcher-6] INFO AuctionSystem.BankActor$ -  ✅ sale concluded ✅
  [scenario-akka.actor.default-dispatcher-8] INFO AuctionSystem.Bidder - Alice added ➕ item: vase
  [scenario-akka.actor.default-dispatcher-5] INFO AuctionSystem.SellerActor - Seller removed item from inventory ❌
  [scenario-akka.actor.default-dispatcher-6] INFO AuctionSystem.BankActor$ - Alice bought the item, new balance 📉 is 70
  [scenario-akka.actor.default-dispatcher-6] INFO AuctionSystem.SellerActor - grace period ended for vase ⏰ returning not possible anymore
  [scenario-akka.actor.default-dispatcher-5] INFO AuctionSystem.AuctionActor - Auction  process terminated 🔚
  */