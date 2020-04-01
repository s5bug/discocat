package org.discordscala.discocat

package ws.event

import io.circe.generic.extras.Configuration
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._
import org.discordscala.discocat.ws.Event

case class Hello[F[_]](client: Client[F], d: HelloData) extends Event[F] {

  override type A = HelloData
  override val encoder: Encoder[HelloData] = HelloData.helloDataEncoder

  override val op: Int = 10
  override val t: Option[String] = None

}

case class HelloData(heartbeatInterval: Int)

object HelloData {

  implicitly[Configuration]

  implicit val helloDataEncoder: Encoder[HelloData] = deriveEncoder
  implicit val helloDataDecoder: Decoder[HelloData] = deriveDecoder

}
