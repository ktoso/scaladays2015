package com.typesafe

import akka.util.ByteString
import akka.stream._
import akka.stream.scaladsl._
import akka.stream.stage._
import java.nio.ByteOrder

object Protocols {

  trait Message
  case class Ping(id: Int) extends Message
  case class Pong(id: Int) extends Message

  def toBytes(msg: Message): ByteString = {
    implicit val order = ByteOrder.LITTLE_ENDIAN
    msg match {
      case Ping(id) => ByteString.newBuilder.putByte(1).putInt(id).result()
      case Pong(id) => ByteString.newBuilder.putByte(2).putInt(id).result()
    }
  }

  def fromBytes(bytes: ByteString): Message = {
    implicit val order = ByteOrder.LITTLE_ENDIAN
    val it = bytes.iterator
    it.getByte match {
      case 1     => Ping(it.getInt)
      case 2     => Pong(it.getInt)
      case other => throw new RuntimeException(s"parse error: expected 1|2 got $other")
    }
  }

  val framing = BidiFlow() { b =>
    implicit val order = ByteOrder.LITTLE_ENDIAN

    def addLengthHeader(bytes: ByteString) = {
      val len = bytes.length
      ByteString.newBuilder.putInt(len).append(bytes).result()
    }

    class FrameParser extends PushPullStage[ByteString, ByteString] {
      // this holds the received but not yet parsed bytes
      var stash = ByteString.empty
      // this holds the current message length or -1 if at a boundary
      var needed = -1

      override def onPush(bytes: ByteString, ctx: Context[ByteString]) = {
        stash ++= bytes
        run(ctx)
      }
      override def onPull(ctx: Context[ByteString]) = run(ctx)
      override def onUpstreamFinish(ctx: Context[ByteString]) =
        if (stash.isEmpty) ctx.finish()
        else ctx.absorbTermination() // we still have bytes to emit

      private def run(ctx: Context[ByteString]): Directive =
        if (needed == -1) {
          // are we at a boundary? then figure out next length
          if (stash.length < 4) pullOrFinish(ctx)
          else {
            needed = stash.iterator.getInt
            stash = stash.drop(4)
            run(ctx) // cycle back to possibly already emit the next chunk
          }
        } else if (stash.length < needed) {
          // we are in the middle of a message, need more bytes
          pullOrFinish(ctx)
        } else {
          // we have enough to emit at least one message, so do it
          val emit = stash.take(needed)
          stash = stash.drop(needed)
          needed = -1
          ctx.push(emit)
        }

      /*
       * After having called absorbTermination() we cannot pull any more, so if we need
       * more data we will just have to give up.
       */
      private def pullOrFinish(ctx: Context[ByteString]) =
        if (ctx.isFinishing) ctx.finish()
        else ctx.pull()
    }

    val outbound = b.add(Flow[ByteString].map(addLengthHeader))
    val inbound = b.add(Flow[ByteString].transform(() => new FrameParser))
    BidiShape(outbound, inbound)
  }

  val chopUp = BidiFlow() { b =>
    val f = Flow[ByteString].mapConcat(_.map(ByteString(_)))
    BidiShape(b.add(f), b.add(f))
  }

  val accumulate = BidiFlow() { b =>
    val f = Flow[ByteString].grouped(1000).map(_.fold(ByteString.empty)(_ ++ _))
    BidiShape(b.add(f), b.add(f))
  }
}
