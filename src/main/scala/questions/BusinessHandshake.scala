package questions

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}

import scala.concurrent.Await
import scala.concurrent.duration.{Duration, DurationInt}

// Messages
trait Contract
case class SignContract(signature: String, replyTo: ActorRef[Contract]) extends Contract
case object ContractSigned extends Contract
case class CannotSignContract(reason: String) extends Contract

// Actors
object Saga:
  def apply(firstPart: ActorRef[Contract], secondPart: ActorRef[Contract], signature: String):
    Behavior[Contract] = passToTheFirstPart(firstPart, secondPart, signature)

  def passToTheFirstPart(firstPart: ActorRef[Contract], secondPart: ActorRef[Contract], signature: String):
    Behavior[Contract] = Behaviors.setup { context =>
      context.log.info(s"Send contract to the 1st part. Signature: $signature")

      firstPart ! SignContract(signature, context.self)
      context.setReceiveTimeout(5.seconds, CannotSignContract("Time reached!"))

      ???
  }

  def passToTheSecondPart(secondPart: ActorRef[Contract], signature: String):
    Behavior[Contract] = Behaviors.setup { context =>
      context.log.info(s"Send contract to the 2nd part. Signature: $signature")

      secondPart ! SignContract(signature, context.self)
      context.setReceiveTimeout(5.seconds, CannotSignContract("Time reached!"))

      ???
  }

object FirstPartContract:
  def apply(): Behavior[Contract] = ???
  

object SecondPartContract:
  def apply(): Behavior[Contract] = ???

object Executor:
  def apply(): Behavior[Contract] = Behaviors.setup { context =>
    val firstPartActor = context.spawn(FirstPartContract(), name = "FirstPart")
    val secondPartActor = context.spawn(SecondPartContract(), name = "SecondPart")

    context.spawn(Saga(firstPartActor, secondPartActor, "SecretSignature"), name = "Saga")
    context.system.terminate()

    Behaviors.same
  }

object BusinessHandshake extends App:
  val system: ActorSystem[Contract] = ActorSystem(Executor(), "BusinessHandshake")
  Await.ready(system.whenTerminated, Duration.Inf)