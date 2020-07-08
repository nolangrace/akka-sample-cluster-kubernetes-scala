package akka.sample.cluster.kubernetes

import java.util.Calendar

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ClusterEvent
import akka.cluster.typed.{Cluster, Subscribe}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.javadsl.AkkaManagement
import akka.stream.scaladsl.{Sink, Source}
import akka.{actor => classic}

import scala.concurrent.Future
import scala.concurrent.duration._

object DemoApp extends App {

  ActorSystem[Nothing](Behaviors.setup[Nothing] { context =>
    import akka.actor.typed.scaladsl.adapter._
    implicit val classicSystem: classic.ActorSystem = context.system.toClassic
    implicit val ec = context.system.executionContext

    val cluster = Cluster(context.system)
    context.log.info("Started [" + context.system + "], cluster.selfAddress = " + cluster.selfMember.address + ")")

    Http().bindAndHandle(complete("Hello world"), "0.0.0.0", 8080)

    // Create an actor that handles cluster domain events
    val listener = context.spawn(Behaviors.receive[ClusterEvent.MemberEvent]((ctx, event) => {
      ctx.log.info("MemberEvent: {}", event)
      Behaviors.same
    }), "listener")

    Cluster(context.system).subscriptions ! Subscribe(listener, classOf[ClusterEvent.MemberEvent])

    AkkaManagement.get(classicSystem).start()
    ClusterBootstrap.get(classicSystem).start()

    val rand = Math.random()

    Source.repeat("Element")
      .throttle(100, 1.second)
      .map(x => System.currentTimeMillis())
      .mapAsync(10)(startTime => {
        Future {
          Thread.sleep((startTime % (150 * 1000)) * 100 )

          startTime
        }
      })
      .map(startTime => {
        val end = System.currentTimeMillis()
        end - startTime
      })
      .to(Sink.foreach(println))
      .named("slow-stream")
      .run()

    Source.repeat("Element")
      .throttle(1000, 1.second)
      .map(x => System.currentTimeMillis())
      .to(Sink.ignore)
      .named("fast-stream")
      .run()


    Behaviors.empty
  }, "appka")
}
