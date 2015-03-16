package com.typesafe

import akka.actor.ActorDSL._
import akka.stream.{ ActorFlowMaterializer, BidiShape }
import akka.stream.scaladsl._
import akka.actor.ActorSystem
import FlowGraph.Implicits._
import akka.pattern._
import scala.concurrent.duration._
import scala.concurrent._
import java.net.InetSocketAddress
import akka.util.ByteString
import akka.http._
import akka.http.server._
import Directives._
import akka.http.model.StatusCodes
import akka.http.marshallers.xml.ScalaXmlSupport._
import akka.util.Timeout

object TheDemo extends App {
  implicit val sys = ActorSystem("TheDemo")
  implicit val mat = ActorFlowMaterializer()
  implicit val timeout = Timeout(3.seconds)
  import sys.dispatcher

  val numbers = Source(List(1, 2, 3))
  val strings = Source(List("a", "b", "c"))

  val pairs = Source() { implicit b =>
    val zip = b.add(Zip[Int, String]())

    numbers ~> zip.in0
    strings ~> zip.in1

    zip.out
  }
  // composite elements

  pairs.filter(_._1 == 3).map { case (n, s) => s"id: $n name: $s" }.runForeach(println)
  // show all the combinators

  val fast = Source(() => Iterator from 0)
  // explain reusability

  //fast.map(x => { Thread.sleep(1000); x }).take(20).runForeach(println)
  // explain back-pressure

  val single = Flow[Int].withAttributes(OperationAttributes.inputBuffer(1, 1))
  //fast.mapAsync(x => after(1.second, sys.scheduler)(Future.successful(x))).via(single).take(20).runForeach(println).onComplete(_ => sys.shutdown())

  // jsuereth/streamerz

  import StreamTcp._
  val port = new InetSocketAddress("localhost", 0)
  val serverSource: Source[IncomingConnection, Future[ServerBinding]] = StreamTcp().bind(port)
  val server = serverSource.to(Sink.foreach(conn => conn.flow.join(Flow[ByteString]).run())).run()
  val local = Await.result(server, 1.second)
  println(local)

  val out = StreamTcp().outgoingConnection(local.localAddress)
  Source(List(1, 2, 3)).map(i => ByteString(s"msg $i")).via(out).map(_.decodeString("ASCII")).runForeach(println)

  class Tick
  val ticks = Source(1.second, 1.second, new Tick)

  val slowItDown = Flow(ticks) { implicit b =>
    tickShape =>
      val zip = b.add(ZipWith((_: Tick, i: Int) => i))
      tickShape ~> zip.in0
      (zip.in1, zip.out)
  }

  //  numbers.via(slowItDown).runForeach(println).onComplete(_ => sys.shutdown())
  val materializedTicks = Source(() => Iterator from 0).viaMat(slowItDown)(Keep.right).to(Sink.foreach(println)).run()
  //  Thread.sleep(3500)
  materializedTicks.cancel()
  println("canceled")

  import Protocols._

  val codec = BidiFlow() { implicit b =>
    val top = Flow[Message].map(toBytes)
    val bottom = Flow[ByteString].map(fromBytes)

    BidiShape(b.add(top), b.add(bottom))
  }

  val handler = Flow() { implicit b =>
    val f = b.add(framing)

    Flow[Message].collect { case Ping(id) => Pong(id) } <~> codec <~> f

    (f.in2, f.out1)
  }

  val local2 = Await.result(StreamTcp().bindAndHandle(handler, port), 1.second)
  println(local2)

  val out2 = codec.atop(framing).join(StreamTcp().outgoingConnection(local2.localAddress))
  //  Source(0 to 9).map(Ping).via(out2).runForeach(println).onComplete(_ => sys.shutdown())

  case class Add(x: Int, y: Int)
  val a = actor(new Act {
    become {
      case Add(x, y) => sender() ! (x + y)
    }
  })

  val route =
    pathPrefix("home") {
      pathPrefix("files") {
        getFromBrowseableDirectory("/Users/rkuhn/comp/demo/http")
      } ~
        path("params") {
          parameterMap { p =>
            complete(p.map { case (k, v) => s"Parameter $k => $v" }.mkString("Params:\n", "\n", ""))
          }
        } ~
        path("html") {
          complete(<html><head></head><body><p>Hello World!</p></body></html>)
        } ~
        path("calc" / IntNumber / IntNumber) { (x, y) =>
          complete(a ? Add(x, y) collect { case z: Int => s"result: $z" })
        }
    } ~
      path("exit") {
        sys.scheduler.scheduleOnce(1.second)(sys.shutdown())
        complete(StatusCodes.OK)
      }
  val http = Http().bindAndStartHandlingWith(route, "localhost", 8080)
  println(Await.result(http, 1.second))
}
