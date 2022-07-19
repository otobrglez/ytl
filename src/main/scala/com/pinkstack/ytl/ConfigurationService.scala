package com.pinkstack.ytl

import zio.{Task, ZLayer}
import zio.ZIO.{fail, fromEither, succeed}
import zio.json.*
import zio.json.yaml.*

case class ChannelConfiguration(id: String, lists: Array[String])
object ChannelConfiguration:
  given channelDecoder: JsonDecoder[ChannelConfiguration] = DeriveJsonDecoder.gen[ChannelConfiguration]

class ConfigurationService:
  import ChannelConfiguration.channelDecoder

  def yamlToChannels(content: String): Task[Vector[ChannelConfiguration]] =
    fromEither(content.fromYaml[Vector[ChannelConfiguration]]).foldZIO(
      error => fail(new Exception(error)),
      value => succeed(value)
    )

  def channels(): Task[Vector[ChannelConfiguration]] =
    Resources.read("channels.yml").flatMap(yamlToChannels)

object ConfigurationService:
  def create(): ConfigurationService                   = new ConfigurationService()
  val live: ZLayer[Any, Nothing, ConfigurationService] = ZLayer.succeed(create())
