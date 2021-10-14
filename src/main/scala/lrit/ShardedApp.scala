import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ClusterSharding
import akka.cluster.sharding.ClusterShardingSettings
import ShardedActor._
import com.typesafe.config.ConfigFactory
object ShardedApp extends App {    

    val baseConfig = ConfigFactory.load()

    val configNode1 = ConfigFactory.parseString(
        """
            akka.actor.provider=cluster
            akka.cluster.seed-nodes = [ "akka://sharding-example@127.0.0.1:20000" ]
            akka.remote.artery {
                canonical {
                    hostname = "127.0.0.1"
                    port = 20000
                }
            }
            akka {
                persistence {
                    journal {
                        plugin = "jdbc-journal"
                        auto-start-journals = ["jdbc-journal"]
                    }
                    snapshot-store {
                        plugin = "jdbc-snapshot-store"
                        auto-start-snapshot-stores = ["jdbc-snapshot-store"]
                    }
                }
            }
            
            akka-persistence-jdbc {
                shared-databases {
                    slick {
                    profile = "slick.jdbc.PostgresProfile$"
                    db {
                        host = "localhost"
                        host = ${?DB_HOST}
                        url = "jdbc:postgresql://"${akka-persistence-jdbc.shared-databases.slick.db.host}":5432/lrit-akka?reWriteBatchedInserts=true"
                        user = "postgres"
                        user = ${?DB_USER}
                        password = "postgres"
                        password = ${?DB_PASS}
                        driver = "org.postgresql.Driver"
                        numThreads = 5
                        maxConnections = 5
                        minConnections = 1
                    }
                    }
                }
            }
            jdbc-journal {
                use-shared-db = "slick"
            }

            jdbc-snapshot-store {
                use-shared-db = "slick"
            }

            jdbc-read-journal {
                use-shared-db = "slick"
            }
        """
    ).resolve()

    val system = ActorSystem("sharding-example", configNode1.withFallback(baseConfig))
    
    val cluster = Cluster(system)

    val extractEntityId: ShardRegion.ExtractEntityId = {
        case EntityEnvelope(id, payload) => (id.toString, payload)
        case msg @ Get(id)               => (id.toString, msg)
    }

    val numberOfShards = 200

    val extractShardId: ShardRegion.ExtractShardId = {
        case EntityEnvelope(id, _)       => (id % numberOfShards).toString
        case Get(id)                     => (id % numberOfShards).toString
        case ShardRegion.StartEntity(id) =>
            // StartEntity is used by remembering entities feature
            (id.toLong % numberOfShards).toString
        case _ => throw new IllegalArgumentException()
    }

    val entities = (1 to 5).map(humanId =>     
        ClusterSharding(system).start(
            typeName = "ShardedActor",
            entityProps = ShardedActor.props(humanId.toString()),
            settings = ClusterShardingSettings(system),
            extractEntityId = extractEntityId,
            extractShardId = extractShardId
        )
    )

    entities(0) ! EntityEnvelope(1, RxCommand("dt1"))
    entities(0) ! EntityEnvelope(1, RxCommand("dt2"))
    entities(0) ! EntityEnvelope(1, RxCommand("dt3"))

}