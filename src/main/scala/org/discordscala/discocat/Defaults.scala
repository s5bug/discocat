package org.discordscala.discocat

import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.TimeUnit

import cats.effect.concurrent.Deferred
import cats.effect.{Concurrent, ConcurrentEffect, ContextShift, Sync, Timer}
import fs2.Stream
import io.circe.DecodingFailure
import org.discordscala.discocat.model.Message
import org.discordscala.discocat.ws.{Event, EventDecoder, EventStruct, Socket}
import org.discordscala.discocat.ws.event.{Heartbeat, HeartbeatAck, Hello, HelloData, Identify, IdentifyData, MessageCreate, Ready, ReadyData}
import spinoco.fs2.http
import spinoco.fs2.http.HttpClient

import scala.concurrent.duration.FiniteDuration

object Defaults {

  def httpClient[F[_] : ConcurrentEffect : ContextShift : Timer](implicit ag: AsynchronousChannelGroup): F[HttpClient[F]] = http.client[F]()

  def socketDeferred[F[_] : Concurrent]: F[Deferred[F, Socket[F]]] = Deferred[F, Socket[F]]

  def defaultEventHandler[F[_] : Timer : Concurrent]: EventHandler[F] =
    sequenceRef =>
      _.flatMap {
        case Hello(cli, HelloData(interval)) =>
          val beat: Stream[F, Unit] = for {
            time <- Stream[F, FiniteDuration](FiniteDuration.apply(0, TimeUnit.MILLISECONDS)) ++ Stream.awakeEvery[F](
              FiniteDuration(interval.signed, TimeUnit.MILLISECONDS)
            )
            sock <- Stream.eval(cli.deferredSocket.get)
            seq <- Stream.eval(sock.sequence.get)
            heartbeat = Heartbeat(cli, seq)
            _ <- Stream.eval(Sync[F].delay(println(s"Heartbeating at $time")))
            sent <- Stream.eval(sock.send(heartbeat))
          } yield sent
          val identify: Stream[F, Unit] = for {
            sock <- Stream.eval(cli.deferredSocket.get)
            identify = Identify(cli, IdentifyData(cli.token))
            sent <- Stream.eval(sock.send(identify))
          } yield sent
          identify ++ beat
        case _ => Stream.empty
      }

  val defaultEventDecoder: EventDecoder = new EventDecoder {
    override def decode[F[_]](client: Client[F]): PartialFunction[EventStruct, Either[DecodingFailure, Event[F]]] = {
      case EventStruct(0, Some("READY"), d) =>
        d.as[ReadyData].map(Ready(client, _))
      case EventStruct(0, Some("MESSAGE_CREATE"), d) =>
        d.as[Message].map(MessageCreate(client, _))
      case EventStruct(10, None, d) =>
        d.as[HelloData].map(Hello(client, _))
      case EventStruct(11, None, _) =>
        Right(HeartbeatAck(client))
    }
  }

}