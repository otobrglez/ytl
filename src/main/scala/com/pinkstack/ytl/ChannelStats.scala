package com.pinkstack.ytl

import com.pinkstack.loglog.HttpClient
import io.circe.parser.parse as parseJSON
import io.circe.{ACursor, Decoder, HCursor, Json}
import org.asynchttpclient.Dsl.get
import org.asynchttpclient.{Request, RequestBuilder}
import zio.Console.printLine
import zio.ZIO
import zio.ZIO.{attempt, fail, fromEither, fromOption, fromTry, service, succeed}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.text.NumberFormat
import java.util.regex.Pattern

object ChannelStats:

  given Conversion[RequestBuilder, Request] with
    def apply(builder: RequestBuilder): Request = builder.build()

  private def base(path: String): RequestBuilder =
    val cookieValue = s"YES+cb.20220208-17-p0.en-GB+FX+${scala.util.Random.between(100, 999)}"
    get("https://www.youtube.com" + path)
      .setHeader("Accept-Language", "en-us,en;q=0.5")
      .addHeader("Cookie", s"CONSENT=${cookieValue};")
      .addQueryParam("hl", "en")
      .addQueryParam("persist_hl", "1")

  private def readInitialData(page: String): ZIO[Any, Throwable, String] =
    for
      pattern <- attempt(Pattern.compile("var ytInitialData\\s*=\\s*(.*);</script>"))
      matcher <- attempt(pattern.matcher(page))
      content <-
        if (!matcher.find()) fail(new RuntimeException("Could not find \"ytInitialData\"."))
        else attempt(matcher.group(1))
    yield content

  private def fetch(path: String): ZIO[HttpClient, Throwable, String] =
    service[HttpClient].flatMap(_.execute(base(path)))

  private def fetchInitialData(path: String): ZIO[HttpClient, Throwable, String] =
    fetch(path).flatMap(readInitialData)

  private def fetchJsonData(path: String): ZIO[HttpClient, Throwable, Json] =
    fetchInitialData(path).flatMap(content => fromEither(parseJSON(content)))

  private def dumpContent(path: String, content: String) =
    ZIO.attempt(
      Files.write(Paths.get(path), content.getBytes(StandardCharsets.UTF_8))
    ) *> succeed(println(s"Written to ${path}"))

  def fetchChannel(channelID: ChannelID): ZIO[HttpClient, Throwable, Channel] =
    for
      json        <- fetchJsonData(s"/channel/$channelID")
      channelData <- fromOption {
        json.\\("microformat").headOption.flatMap { j =>
          for
            renderer    <- j.\\("microformatDataRenderer").headOption
            title       <- renderer.\\("title").headOption.flatMap(_.asString)
            description <- renderer.\\("description").headOption.flatMap(_.asString)
          yield Channel(title, description)
        }
      }.orDieWith(_ => new Exception("JSON parsing has failed."))
    yield channelData

  def traverseJsonFields(fields: String*)(json: Json) =
    json
      .downFields(fields*)
      .focus
      .flatMap(_.asArray)
      .getOrElse(Vector.empty[Json])

  private def parsePlaylist(json: Json): ZIO[Any, Throwable, Playlist] =
    for
      microformat               <- fromOption(json.downFields("microformat", "microformatDataRenderer").focus)
        .orDieWith(_ => new Exception("microformat not found."))
      title                     <- fromTry(microformat.hcursor.getTry[String]("title"))
      description               <- fromTry(microformat.hcursor.getTry[String]("description"))
      contentTabs               <- attempt(
        traverseJsonFields("contents", "twoColumnBrowseResultsRenderer", "tabs")(json)
      )
      sectionListRenderer       <- attempt(
        contentTabs.flatMap(traverseJsonFields("tabRenderer", "content", "sectionListRenderer", "contents"))
      )
      itemSectionRenderer       <- attempt(
        sectionListRenderer.flatMap(traverseJsonFields("itemSectionRenderer", "contents"))
      )
      playlistVideoListRenderer <- attempt(
        itemSectionRenderer.flatMap(traverseJsonFields("playlistVideoListRenderer", "contents"))
      )
      videos                    <- attempt {
        playlistVideoListRenderer.flatMap(json =>
          for
            item    <- json.hcursor.downField("playlistVideoRenderer").focus
            videoID <- item.hcursor.get[String]("videoId").toOption
            title   <- item.hcursor.downFields("title", "runs").downArray.get[String]("text").toOption
          yield Video(videoID, title)
        )
      }
    yield Playlist(title, description, videos)

  def fetchPlaylist(playlistID: PlaylistID): ZIO[HttpClient, Throwable, Playlist] =
    fetchJsonData(s"/playlist?list=$playlistID").flatMap(parsePlaylist)

  private def parseVideo(videoID: VideoID)(json: Json): ZIO[Any, Throwable, Video] =
    for
      item <- fromOption(
        json.hcursor
          .downFields("contents", "twoColumnWatchNextResults", "results", "results", "contents")
          .downArray
          .downField("videoPrimaryInfoRenderer")
          .focus
      ).orDieWith(_ => new Exception(s"Video item was not found for video ${videoID}"))

      title <- fromOption(item.hcursor.downFields("title", "runs").downArray.get[String]("text").toOption)
        .orDieWith(_ => new Exception("Title not found."))

      rawCount  <- attempt(
        item.hcursor
          .downFields("viewCount", "videoViewCountRenderer", "viewCount", "runs")
          .downArray
          .get[String]("text")
          .toOption
      )
      viewCount <- attempt(rawCount.map(content => NumberFormat.getInstance().parse(content).intValue()))
    yield Video(videoID, title, viewCount = viewCount)

  def fetchVideo(videoID: VideoID): ZIO[HttpClient, Throwable, Option[Video]] =
    fetchJsonData(s"/watch?v=${videoID}")
      // .flatMap(json => dumpContent(s"video-${videoID}.json", json.toString) *> succeed(json))
      .flatMap(parseVideo(videoID))
      .absorb
      .map(v => Some(v))
      .catchAll(_ => succeed(None))
