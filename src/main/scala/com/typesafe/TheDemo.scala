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
import scala.annotation.tailrec
import akka.io.Inet

object TheDemo extends App {
  implicit val sys = ActorSystem("TheDemo")
  implicit val mat = ActorFlowMaterializer()
  implicit val timeout = Timeout(3.seconds)
  import sys.dispatcher

  val numbers = Source(List(1, 2, 3))
  val strings = Source(List("a", "b", "c"))

  val composite = Source() { implicit b =>
    val zip = b.add(Zip[Int, String]())

    numbers ~> zip.in0
    strings ~> zip.in1

    zip.out
  }

  val fast = Source(() => Iterator from 0)

  val single = Flow[Int].withAttributes(OperationAttributes.inputBuffer(1, 1))
  val f = Flow[ByteString].mapAsync(x => after(1.second, sys.scheduler)(Future.successful(x)))
  //    .via(single)
  //    .runForeach(println)

  import Protocols._

  val codec = BidiFlow() { implicit b =>
    val top = b.add(Flow[Message].map(toBytes))
    val bottom = b.add(Flow[ByteString].map(fromBytes))

    BidiShape(top, bottom)
  }

  val protocol = codec atop framing

  val addr = new InetSocketAddress("localhost", 0)
  val server = StreamTcp().bind(addr).to(Sink.foreach { conn =>
    conn.flow.join(protocol.reversed).join(Flow[Message]
      .collect {
        case Ping(id) => Pong(id)
      }).run()
  }).run()
  val myaddr = Await.result(server, 1.second)

  val client = StreamTcp().outgoingConnection(myaddr.localAddress)
  val stack = protocol join client

  Source(0 to 10).map(Ping).via(stack).runForeach(println)

  val route =
    pathPrefix("demo") {
      getFromBrowseableDirectory("/Users/rkuhn/comp/demo/http")
    } ~
      path("upload") {
        extractRequest { req =>
          req.entity.dataBytes.via(f).to(Sink.ignore).run()
          complete(StatusCodes.OK)
        }
      }

  Http().bind("localhost", 8080).runForeach(conn => conn.flow.join(route).run())
}
