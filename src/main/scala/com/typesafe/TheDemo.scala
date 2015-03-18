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

}
