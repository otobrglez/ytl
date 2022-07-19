package com.pinkstack.ytl

import com.pinkstack.loglog.{HttpClient, HttpClientConfig, HttpClientLive}
import zio.Console.printLine
import zio.Schedule.{spaced, succeed}
import zio.ZIO.{foreachPar, service, succeed}
import zio.*

object StatsCollectorApp extends zio.ZIOAppDefault:
  type Videos = Queue[VideoID]

  def collectPlaylists(playlists: Vector[String], videos: Videos): ZIO[HttpClient, Throwable, Unit] =
    foreachPar(playlists)(playlistID =>
      for
        playlist <- ChannelStats.fetchPlaylist(playlistID)
        _        <- printLine(s"Playlist title: ${playlist.title}")
        _        <- videos.offerAll(playlist.videos.map(_.videoID))
      yield ()
    ).withParallelism(4).unit

  def collectLiveVideos(videos: Videos, n: Int = 4): ZIO[HttpClient, Throwable, Unit] =
    for
      ids   <- videos.takeN(n)
      stats <- foreachPar(ids.toList)(ChannelStats.fetchVideo).withParallelism(n)

      // Do the pretty printing.
      _ <- printLine(stats.flatten.map { case Video(_, title, viewCount) =>
        s"-- $title: $viewCount"
      }.mkString("\n"))
    yield ()

  val program: ZIO[ConfigurationService & HttpClient, Throwable, Unit] =
    for
      // Observing only channels from configuration file.
      channels <- service[ConfigurationService].flatMap(_.channels())

      // Queue for intermediate video IDs to fetch
      videos <- Queue.bounded[VideoID](200)

      // Loop over playlists and collect stats for "live" videos.
      _ <- collectPlaylists(channels.flatMap(_.lists), videos)
        .repeat(spaced(30.seconds))
        .raceFirst(collectLiveVideos(videos).repeat(spaced(200.millis)))
    yield ()

  def run = program.provideLayer(
    ConfigurationService.live ++ HttpClientLive.live(
      HttpClientConfig(2000, 2000)
    )
  )
