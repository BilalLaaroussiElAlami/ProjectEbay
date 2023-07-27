package solutions


import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}


trait Message
case class SimpleMessage(content:String) extends Message

/*
object Seller{
  def apply():Behavior[Message] = {
    Behaviors.setup(context => new Seller(context))
  }
}


class Seller(context:ActorContext[Message]) extends AbstractBehavior[Message](context){
  override def onMessage(message: Message): AbstractBehavior[Message] = {
    message match
      case m: Message => println("ok");this
  }
}

object Main extends App:
  Seller
*/



object WithAskPatternManual:
  def apply(): Behavior[Message] =
    Behaviors.setup(context => new WithAskPatternManual(context))

class WithAskPatternManual(context: ActorContext[Message])
  extends AbstractBehavior[Message](context):
  override def onMessage(message: Message): AbstractBehavior[Message] =
    message match {
      case SimpleMessage(replyTo) =>
        println("ok")
        this
    }

object Main extends App:
  val system: ActorSystem[Message] = ActorSystem(WithAskPatternManual(), "ok")
  system ! SimpleMessage("ok")
  system ! SimpleMessage("ok")
