package lrit

import akka.actor.Props
import akka.actor.ActorLogging
import akka.persistence.PersistentActor
import akka.cluster.sharding.ShardRegion
import lrit.ShardedActor.Event

object ShardedActor {
    case object Done
    case object Stop
    case class RxCommand(data:String)
    case class Event(data:String)

    final case class Get(shardedEntityId: Long)
    final case class EntityEnvelope(shardedEntityId: Long, payload: Any)

    def props() = Props(new ShardedActor())
}
class ShardedActor() extends PersistentActor with ActorLogging {
    
    var data:List[String] = List()

    def updateState(e:ShardedActor.Event) = {
        log.info(s"Prepending ${e.data} to $data.")
        data = e.data :: data
    }

    override def receiveRecover = {
        case e:ShardedActor.Event => updateState(e)
    }

    override def receiveCommand = {
        case ShardedActor.RxCommand(d) => updateState(Event(d))
        case ShardedActor.Done => context.parent ! ShardRegion.Passivate(stopMessage = ShardedActor.Stop)
    }

    override def persistenceId:String = s"sharded-entity-${self.path.name}"
}