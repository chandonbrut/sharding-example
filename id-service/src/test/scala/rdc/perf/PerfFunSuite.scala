package rdc.perf

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest._
import flatspec._
import matchers._
import akka.actor.ActorSystem
import rdc.IdActor
import akka.actor.Props
import rdc.NewId
import rdc.Id
import akka.pattern.ask
import scala.concurrent.duration._
import akka.util.Timeout
import scala.concurrent.Await
import akka.cluster.Cluster
import scala.util.Random

class IdActorSpec extends AnyFlatSpec with should.Matchers {

    implicit val timeout: Timeout = Timeout(100 millis)
    "IdActor" should "generate ids sequentially" in {
        val idSystem = ActorSystem("id")
        val cluster = Cluster(idSystem)
        val idActors = (1 to 5).map(i => idSystem.actorOf(Props[IdActor]()))
         

        (1 to 500000).map(i => {
            val id = Await.result((idActors(Random.between(0, 5)) ? NewId).mapTo[Id], Duration.Inf)
            id.value should be (i % 100000)
        })

    }  
}