package com.typesafe

import akka.actor.ActorSystem
import akka.http.scaladsl._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl._
import akka.pattern._
import akka.stream.stage.{Context, PushStage, SyncDirective, TerminationDirective}
import akka.util.{ByteString, Timeout}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.StdIn

object TheDemo extends App with MyStages {
  require(args.length == 2, "Usage: run [port] [delay_string]")
  val port = args(0).toInt
  val delayMs = Duration.apply(args(1)).asInstanceOf[FiniteDuration]

  implicit val sys = ActorSystem("TheDemo")
  implicit val mat = ActorFlowMaterializer()
  implicit val timeout = Timeout(3.seconds)

  import sys.dispatcher

  val slowProcessing =
    Flow[ByteString]
      .mapAsync(parallelism = 1)(x => after(delayMs, sys.scheduler)(Future.successful(x)))
      .map(identity)
      .transform(() => printOnComplete())

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


  Http().bindAndHandle(route, "127.0.0.1", port)
  println(s"bound to = $port")





  StdIn.readLine("[hit enter to complete]\n")
  sys.shutdown()
}

trait MyStages {
  def printOnComplete() = new PushStage[Any, Any] {
    private val startTime = System.currentTimeMillis()

    override def onPush(elem: Any, ctx: Context[Any]): SyncDirective = ctx.push(elem)
    override def onUpstreamFinish(ctx: Context[Any]): TerminationDirective = {
      println(s"Finished upload, took: ${(System.currentTimeMillis() - startTime)}ms")
      ctx.finish()
    }
  }
}