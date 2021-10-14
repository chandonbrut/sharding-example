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
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement

/**
  * A setting is provided, akka.management.cluster.bootstrap.new-cluster-enabled that can be disable new cluster formation to only allow the node to join existing clusters.
    On initial deployment use the default akka.management.cluster.bootstrap.new-cluster-enabled=on
    Following the initial deployment it is recommended to set akka.management.cluster.bootstrap.new-cluster-enabled=off with an immediate re-deployment once the initial cluster has formed
  */

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

    def produceNode(host:String, port:Int) = {
        val nodeConf:String = s"""
                        # akka.management.http.port = $port
                        akka.management.http.hostname = $host
                        akka.management.cluster.bootstrap.contact-point-discovery.discovery-method = config
                        contact-point-discovery.required-contact-point-nr = 2
                        akka.discovery.config.services.sharding-example {
                                            endpoints = [
                                                {
                                                    host = 127.0.0.2
                                                    port = 8558
                                                },
                                                {
                                                    host = 127.0.0.3
                                                    port = 8558
                                                }
                                            ]
                        }

                        akka.management {
                            cluster.bootstrap {
                                contact-point-discovery {
                                    service-name = "sharding-example"
                                    discovery-method = config
                                }
                            }
                        }

                akka.extensions = ["akka.management.cluster.bootstrap.ClusterBootstrap"]
                akka.actor.allow-java-serialization = true
                akka.actor.provider=cluster
                akka.remote.artery {
                    canonical {
                        hostname = $host
                        port = 2551
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

                akka.cluster.shutdown-after-unsuccessful-join-seed-nodes = 30s
                akka.coordinated-shutdown.exit-jvm = o
           """
       
        val baseConfig = ConfigFactory.load()
        val configNode1 = ConfigFactory.parseString(nodeConf).resolve()

        val system = ActorSystem("sharding-example", configNode1.withFallback(baseConfig))
        AkkaManagement(system).start()
        ClusterBootstrap(system).start()
        system
        
    }
}

object DiscoveryApp extends App {
    val node1 = H2Stuff.produceNode("127.0.0.2",2551)
    Thread.sleep(2000)

    val node3 = H2Stuff.produceNode("127.0.0.3",2553)
}

object ShardedApp extends App {
    H2Stuff.start

    val node1 = H2Stuff.produceNode("localhost",20000)
    Thread.sleep(4000)
    val node2 = H2Stuff.produceNode("localhost",20001)

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
        p1 ! EntityEnvelope(i, RxCommand("dtA"))
        p2 ! EntityEnvelope(i, RxCommand("dtB"))
    })

    Thread.sleep(2000)
    Await.result(node2.terminate(),Duration.Inf)

    Thread.sleep(10000)
    (1 to 5).map(i => {
        p1 ! EntityEnvelope(i, RxCommand("dtC"))
    })

    Thread.sleep(500)
    val node2Reborn = H2Stuff.produceNode("localhost",20003)
    Thread.sleep(10000)
    
    val p2Reborn = ClusterSharding(node2Reborn).start(
        typeName = "ShardedActor",
        entityProps = ShardedActor.props(),
        settings = ClusterShardingSettings(node2Reborn),
        extractEntityId = extractEntityId,
        extractShardId = extractShardId
    )    

    (1 to 5).map(i => {
        p2Reborn ! EntityEnvelope(i, RxCommand("dtD"))
    })

    Thread.sleep(2000)

    Await.result(node1.terminate(), Duration.Inf)
    Await.result(node2Reborn.terminate(), Duration.Inf)

}