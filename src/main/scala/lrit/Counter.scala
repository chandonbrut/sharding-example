package lrit

import akka.actor.Actor
import akka.actor.ActorLogging
object Counter {
    case class Add(v:Int)
}
class Counter extends Actor with ActorLogging {
    var count = 0L
    def receive = {
        case Counter.Add(v:Int) =>
            log.info(s"got $v, adding to $count")
            count += v
    }
}