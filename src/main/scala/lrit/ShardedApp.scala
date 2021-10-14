package lrit

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ClusterSharding
import akka.cluster.sharding.ClusterShardingSettings
import ShardedActor._
import com.typesafe.config.ConfigFactory

import java.sql.DriverManager
import scala.concurrent.Await
import scala.concurrent.duration.Duration

object H2Stuff {
    val h2schema = """
        CREATE TABLE IF NOT EXISTS "event_journal" (
        "ordering" BIGINT NOT NULL AUTO_INCREMENT,
        "deleted" BOOLEAN DEFAULT false NOT NULL,
        "persistence_id" VARCHAR(255) NOT NULL,
        "sequence_number" BIGINT NOT NULL,
        "writer" VARCHAR NOT NULL,
        "write_timestamp" BIGINT NOT NULL,
        "adapter_manifest" VARCHAR NOT NULL,
        "event_payload" BLOB NOT NULL,
        "event_ser_id" INTEGER NOT NULL,
        "event_ser_manifest" VARCHAR NOT NULL,
        "meta_payload" BLOB,
        "meta_ser_id" INTEGER,
        "meta_ser_manifest" VARCHAR,
        PRIMARY KEY("persistence_id","sequence_number")
    );

            CREATE UNIQUE INDEX "event_journal_ordering_idx" on "event_journal" ("ordering");

            CREATE TABLE IF NOT EXISTS "event_tag" (
                "event_id" BIGINT NOT NULL,
                "tag" VARCHAR NOT NULL,
                PRIMARY KEY("event_id", "tag"),
                CONSTRAINT fk_event_journal
                FOREIGN KEY("event_id")
                REFERENCES "event_journal"("ordering")
                ON DELETE CASCADE
            );

            CREATE TABLE IF NOT EXISTS "snapshot" (
                "persistence_id" VARCHAR(255) NOT NULL,
                "sequence_number" BIGINT NOT NULL,
                "created" BIGINT NOT NULL,"snapshot_ser_id" INTEGER NOT NULL,
                "snapshot_ser_manifest" VARCHAR NOT NULL,
                "snapshot_payload" BLOB NOT NULL,
                "meta_ser_id" INTEGER,
                "meta_ser_manifest" VARCHAR,
                "meta_payload" BLOB,
                PRIMARY KEY("persistence_id","sequence_number")
                );

            CREATE TABLE IF NOT EXISTS "durable_state" (
                "global_offset" BIGINT NOT NULL AUTO_INCREMENT,
                "persistence_id" VARCHAR(255) NOT NULL,
                "revision" BIGINT NOT NULL,
                "state_payload" BLOB NOT NULL,
                "state_serial_id" INTEGER NOT NULL,
                "state_serial_manifest" VARCHAR,
                "tag" VARCHAR,
                "state_timestamp" BIGINT NOT NULL,
                PRIMARY KEY("persistence_id")
                );
            CREATE INDEX "state_tag_idx" on "durable_state" ("tag");
            CREATE INDEX "state_global_offset_idx" on "durable_state" ("global_offset");
        """

    def start = {
        val h2conn = DriverManager.getConnection("jdbc:h2:mem:sharding")
        val stmt = h2conn.createStatement()
        stmt.execute(h2schema)
    }

    def produceNode(port:Int) = {
        val nodeConf:String = s"""
                akka.actor.allow-java-serialization = true
                akka.actor.provider=cluster
                akka.cluster.seed-nodes = [ "akka://sharding-example@localhost:20000" ]
                akka.remote.artery {
                    canonical {
                        hostname = localhost
                        port = ${port}
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
                            profile = "slick.jdbc.H2Profile$$"
                            db {
                                url = "jdbc:h2:mem:sharding"
                                driver = "org.h2.Driver"
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

        println(nodeConf)
        
        val baseConfig = ConfigFactory.load()
        val configNode1 = ConfigFactory.parseString(nodeConf).resolve()

        val system = ActorSystem("sharding-example", configNode1.withFallback(baseConfig))
        system
        
    }
}

object ShardedApp extends App {
    H2Stuff.start

    val node1 = H2Stuff.produceNode(20000)
    val node2 = H2Stuff.produceNode(20001)

    Thread.sleep(5000)

    val extractEntityId: ShardRegion.ExtractEntityId = {
        case EntityEnvelope(id, payload) => (id.toString, payload)
        case msg @ Get(id)               => (id.toString, msg)
    }

    val numberOfShards = 20

    val extractShardId: ShardRegion.ExtractShardId = {
        case EntityEnvelope(id, _)       => (id % numberOfShards).toString
        case Get(id)                     => (id % numberOfShards).toString
        case ShardRegion.StartEntity(id) =>
            // StartEntity is used by remembering entities feature
            (id.toLong % numberOfShards).toString
        case _ => throw new IllegalArgumentException()
    }

    val p1 = ClusterSharding(node1).start(
        typeName = "ShardedActor",
        entityProps = ShardedActor.props(),
        settings = ClusterShardingSettings(node1),
        extractEntityId = extractEntityId,
        extractShardId = extractShardId
    )
    val p2 = ClusterSharding(node2).start(
        typeName = "ShardedActor",
        entityProps = ShardedActor.props(),
        settings = ClusterShardingSettings(node2),
        extractEntityId = extractEntityId,
        extractShardId = extractShardId
    )

    (1 to 5).map(i => {
        p1 ! EntityEnvelope(i, RxCommand("dt1"))
        p2 ! EntityEnvelope(i, RxCommand("dtA"))
    })

    Thread.sleep(10000)
    Await.result(node2.terminate(),Duration.Inf)

    (1 to 5).map(i => {
        p1 ! EntityEnvelope(i, RxCommand("dtC"))
    })

    Thread.sleep(500)

    Await.result(node1.terminate(), Duration.Inf)

}