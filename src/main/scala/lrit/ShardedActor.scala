import akka.actor.Props
import akka.actor.ActorLogging
import akka.persistence.PersistentActor
import akka.cluster.sharding.ShardRegion

object ShardedActor {
    case object Done
    case object Stop
    case class RxCommand(data:String)
    case class Event(data:String)

    final case class Get(shardedEntityId: Long)
    final case class EntityEnvelope(shardedEntityId: Long, payload: Any)

    def props(humanId:String) = Props(new ShardedActor(humanId))
}
class ShardedActor(humanId:String) extends PersistentActor with ActorLogging {
    
    var data:List[String] = List()

    def updateState(e:ShardedActor.Event) = {
        data = e.data :: data
    }

    override def receiveRecover = {
        case e:ShardedActor.Event => updateState(e)
    }

    override def receiveCommand = {
        case ShardedActor.RxCommand(d) =>
            log.info(s"Appending $d to $data.")
            // persistAsync(ShardedActor.Event(d))(updateState)
        case ShardedActor.Done => context.parent ! ShardRegion.Passivate(stopMessage = ShardedActor.Stop)
    }

    override def persistenceId:String = s"sharded-entity-${humanId}-${self.path.name}"
}