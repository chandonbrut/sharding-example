package example

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import akka.http.javadsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import java.util.Properties
import java.util.UUID
import org.apache.kafka.clients.producer.ProducerRecord
import scala.concurrent.duration._
import java.util.Arrays
import akka.util.Timeout
import akka.http.scaladsl.Http
import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import java.util.Collections
import scala.concurrent.Future

trait Data
trait Command
trait Query extends Command
trait Event

case class Report(messageId: String, timestamp: Long, imo: String, lat: Double, lon: Double) extends Data
case class Ship(imoNum: String, name:String, flag:String, owner:String) extends Data

case class AddedShip(ship: Ship) extends Event
case class DeletedShip(ship: Ship) extends Event
case class ReportEvent(inbound: Boolean, message: Report) extends Event

case class AddShip(ship: Ship) extends Command
case class DeleteShip(ship: Ship) extends Command
case class RxReport(report: Report) extends Command
case class TxReport(report: Report) extends Command
case class Publish(topic: String, key: String, value: String)

case object ListShips extends Query

object Kafka extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val shipFormat = jsonFormat4(Ship)
  implicit val addedShipFormat = jsonFormat1(AddedShip)
  implicit val deletedShipFormat = jsonFormat1(DeletedShip)
  implicit val reportFormat = jsonFormat5(Report)
  implicit val reportEventFormat = jsonFormat2(ReportEvent)
}

class Kafkacer extends Actor with ActorLogging {
  import org.apache.kafka.clients.producer.ProducerConfig
  import org.apache.kafka.common.serialization.StringSerializer
  import scala.jdk.CollectionConverters._
  import org.apache.kafka.clients.producer.KafkaProducer


  val serializerName:String = "org.apache.kafka.common.serialization.StringSerializer"

  val producerProps = new Properties()
  producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
  producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, serializerName)
  producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, serializerName)
  val kafkaProducer = new KafkaProducer[String,String](producerProps)

  def receive: Receive = {
    case Publish(t,k,v) => publish(t,k,v)
  }

  def publish(publishTopic:String, key:String, value:String) = {
    try {    
      val record = new ProducerRecord[String,String](publishTopic, key, value)
      val callback = new Callback() {
        override def onCompletion(x$1: RecordMetadata, x$2: Exception): Unit = {
        }
      }
      kafkaProducer.send(record, callback).get()      
    } catch {
      case e:Exception => e.printStackTrace()        
    }
  }
}

/**
  * Escuta as filas e notifica o parent.  
  */
class Kafkonsumer(consumerId:String) extends Actor with ActorLogging with DefaultJsonProtocol with SprayJsonSupport {  
  import org.apache.kafka.clients.consumer.KafkaConsumer
  import scala.jdk.CollectionConverters._
  import org.apache.kafka.common.serialization.StringSerializer
  import org.apache.kafka.common.serialization.StringDeserializer
  import org.apache.kafka.clients.consumer.ConsumerConfig
  import org.apache.kafka.clients.producer.ProducerConfig  
  

  val serializerName:String = "org.apache.kafka.common.serialization.StringSerializer"
  val deserializerName:String = "org.apache.kafka.common.serialization.StringDeserializer"
  
  implicit val executionContext = context.dispatcher
  
  val topics = List("ship", "rx-report", "tx-report", "component")  

  val consumerProps = new Properties() 
  consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
  consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,deserializerName)
  consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,deserializerName)  

  // garante unicidade dos consumers, útil para broadcast.
  // consumerProps.put("group.id",s"consumer-group-${UUID.randomUUID().toString()}")
  consumerProps.put("group.id",consumerId)

// Não commita o offset ao consumir mensagens, significa que todo consumer pegará todos os dados do topic.
//   consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
//   consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

  def receive: Receive = idle
  
  def idle:Receive = {
    case "start" =>
      val kafkaConsumer = new KafkaConsumer[String,String](consumerProps)
      kafkaConsumer.subscribe(topics.asJava)
      context.become(consuming(kafkaConsumer))
      self ! "consume"
    
  }
  
  def consuming(consumer: KafkaConsumer[String,String]): Receive = {
    case "consume" =>
      val records = consumer.poll(1000)        
        val it = records.iterator.asScala
        val values = for {
            v <- it
        } {
            context.parent ! s"${v.topic()}:${v.value()}"
        }
        self ! "consume"
    case "stop" =>
      consumer.close
      context.become(idle)
  }
  
}

class KafkaUser extends Actor with ActorLogging with DefaultJsonProtocol with SprayJsonSupport {
  val publisher = context.actorOf(Props[Kafkacer])
  

  def receive: Receive = {
    case RxReport(report) =>
      log.info(s"Received report, publishing.")      
      val value = Kafka.reportEventFormat.write(ReportEvent(true, report)).toString()
      publisher ! Publish("rx-report", report.messageId, value)

    case AddShip(ship) =>      
      val key = UUID.randomUUID().toString()
      val value = Kafka.addedShipFormat.write(AddedShip(ship)).toString()
      publisher ! Publish("ship", key, value)
  
    case DeleteShip(ship) =>
      val key = UUID.randomUUID().toString()
      val value = Kafka.deletedShipFormat.write(DeletedShip(ship)).toString()
      publisher ! Publish("ship", key, value)

    case ListShips =>
      // deveria buscar do kafka connect (ou banco local) e retornar
      sender ! List()
  }
}


object Hello extends App with SprayJsonSupport with DefaultJsonProtocol {
  implicit val system = ActorSystem("kafka-service")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  implicit val timeout = Timeout(30.seconds)
  
  import example.Kafka._


  import akka.http.scaladsl.server.Directives._
  import akka.pattern.ask
  
  
  val kafkaBroker = system.actorOf(Props[KafkaUser])


  val route = path("ship") {
                post {
                  entity(as[Ship]) { ship =>
                    system.log.info(s"Received $ship")
                    kafkaBroker ! AddShip(ship)
                    complete(StatusCodes.OK)
                  }
                } ~ get {
                  // complete((kafkaBroker ? ListShips).mapTo[List[String]])
                  complete("kkkkkkk oieeeee")
                } ~ delete { 
                  entity(as[Ship]) { ship =>
                    kafkaBroker ! DeleteShip(ship)
                    system.log.info(s"Deleted $ship")
                    complete(StatusCodes.OK)
                  }
                }
              } ~
              path("report") {
                post {
                  entity(as[Report]) { report =>
                    system.log.info(s"Received $report")
                    kafkaBroker ! RxReport(report)
                    complete(StatusCodes.OK)
                  }
                }
              }

  val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)


}