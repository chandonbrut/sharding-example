package rdc

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.cluster.Cluster
import akka.actor.ActorRef
import akka.cluster.ddata.PNCounterKey
import scala.concurrent.duration._
import akka.cluster.ddata.GCounterKey

case object NewId
case class Id(value: BigInt)
class IdActor extends Actor with ActorLogging {

    val idCounterKey = GCounterKey("id")
  
  	import akka.cluster.ddata._
    implicit val cluster = Cluster(context.system)
    implicit val node = DistributedData(context.system).selfUniqueAddress
    val replicator: ActorRef = DistributedData(context.system).replicator

    val timeout: FiniteDuration = 10 millis

  def receive: Receive = {
    case NewId => 
        val og = sender()
        val upd = Replicator.Update(idCounterKey, GCounter(), Replicator.WriteAll(timeout), request = Some(og))(_ :+ 1)
        replicator ! upd
        replicator ! Replicator.Get(`idCounterKey`, Replicator.ReadMajority(timeout), request = Some(og))

    case reply @ Replicator.GetSuccess(`idCounterKey`, Some(replyTo: ActorRef)) =>
        val id = reply.get(`idCounterKey`).value
        replyTo ! Id(id % 100000) 

    case reply @ Replicator.UpdateSuccess(`idCounterKey`, _) =>    
    case reply @ Replicator.UpdateTimeout(`idCounterKey`, Some(replyTo: ActorRef)) =>

  }

}
