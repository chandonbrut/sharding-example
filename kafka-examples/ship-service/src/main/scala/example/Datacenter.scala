package example

import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import akka.actor.ActorLogging
import akka.actor.Actor
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import java.util.Properties
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.clients.consumer.KafkaConsumer
import scala.concurrent.Future
import akka.actor.Props
import java.util.UUID

object Datacenter {
    import scala.concurrent.duration._
    import scala.concurrent.ExecutionContext.global

    def produceSystem(port: Int, seedPort: Int) = {        
        val fallbackConfig = ConfigFactory.parseString(s"""
            akka.remoting.artery {
                canonical {
                    hostname = "localhost"
                    port = $port
                }
            }
            akka.actor.provider=cluster
            akka.cluster.seed-nodes = ["akka://DataCenterSystem@localhost:$seedPort"]
            """
        ).resolve()
        val config = ConfigFactory.load().withFallback(fallbackConfig)
        val system = ActorSystem("DataCenterSystem", config)

        val manager = system.actorOf(Props[NodeManager], s"manager-$port")
        implicit val executionContext = system.dispatcher
        system.scheduler.scheduleOnce(15.seconds, manager,"stop")
        system
    }

    
    def main(args: Array[String]): Unit = {

        (2551 to 2555).map(i =>  {
            println(s"iniciando $i")
            val s = produceSystem(i,2551)
            Thread.sleep(3000)
        })


    }
    
}

class NodeManager extends Actor with ActorLogging {
    val consumer = context.actorOf(Props( new Kafkonsumer(self.path.name)))
    val producer = context.actorOf(Props[Kafkacer])

    consumer ! "start"

    def receive: Receive = {
        case "stop" => consumer ! "stop"
        case str:String =>
            log.info(s"Got $str")
    }

}