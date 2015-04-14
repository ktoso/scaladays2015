package com.typesafe

import akka.actor.ActorSystem
import akka.http.scaladsl._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl._
import akka.util.{ByteString, Timeout}

import scala.concurrent.duration._

object TheDemo extends App {
  implicit val sys = ActorSystem("TheDemo")
  implicit val mat = ActorFlowMaterializer()
  implicit val timeout = Timeout(3.seconds)
  import sys.dispatcher

  val slowProcessing =
    Flow[ByteString]
//      .mapAsync(parallelism = 1, x => after(1.second, sys.scheduler)(Future.successful(x)))
       .map(identity)

  val route =
    pathPrefix("demo") {
      getFromBrowseableDirectory("/Users/ktoso/code/akka-http-demo")
    } ~
      path("upload") {
        extractRequest { req =>
          println("uploading...")
          req.entity.dataBytes.via(slowProcessing).to(Sink.ignore).run()
          complete(StatusCodes.OK)
        }
      }






  Http().bindAndHandle(route, "127.0.0.1", 8080)
  //  Http().bind("127.0.0.1", 8080)
//    .runForeach(connection => connection.flow.join(route).run())
  println("bound to = 8080")





  readLine("[hit enter to complete]\n")
  sys.shutdown()
}
