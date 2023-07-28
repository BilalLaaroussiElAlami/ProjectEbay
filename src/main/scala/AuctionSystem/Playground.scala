package AuctionSystem

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object Playground:
  def apply():Behavior[Message] = Behaviors.setup { context =>
    context.scheduleOnce(scala.concurrent.duration.FiniteDuration(5,"seconds") , context.self, SimpleMessage("start"))
    Behaviors.receiveMessage {message =>
      message match
        case SimpleMessage("start") => context.log.info("Started"); context.scheduleOnce(scala.concurrent.duration.FiniteDuration(5,"seconds") , context.self, SimpleMessage("start"))
      Behaviors.same
    }
  }

object Mayn extends App:
  val test: ActorSystem[Message] = ActorSystem(Playground(), "Playground")