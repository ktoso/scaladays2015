package com.rolandkuhn

import akka.http.server._
import akka.http.server.Directives._
import scala.concurrent.ExecutionContext
import akka.actor.ActorSystem
import akka.http.Http
import akka.stream._
import akka.stream.scaladsl._
import scala.io.StdIn

object HttpDemo extends App {
  implicit val system = ActorSystem("HttpDemo")
  implicit val mat = ActorFlowMaterializer()
  import system.dispatcher

  val server = Http().bind("localhost", 8080)
  
  val f = server.to(Sink.foreach { conn =>
    println(s"connection from ${conn.remoteAddress}")
    conn.flow.join(route).run()
  }).run()
  
  f.flatMap(s => {
    println(s"bound to ${s.localAddress}")
    StdIn.readLine()
    s.unbind()
  }).map(_ => system.shutdown())
  .recover{
    case e: Exception =>
      e.printStackTrace()
      system.shutdown()
  }

  def route =
    pathPrefix("hello") {
      path("index.html") {
        get {
          complete("Ok")
        }
      }
    }
}