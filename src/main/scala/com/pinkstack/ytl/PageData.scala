package com.pinkstack.ytl

type YoutubeID  = String
type ChannelID  = YoutubeID
type PlaylistID = YoutubeID
type VideoID    = YoutubeID

trait PageData:
  val title: String
  val description: String

case class Channel(title: String, description: String) extends PageData

case class Playlist(title: String, description: String, videos: Vector[Video] = Vector.empty) extends PageData

case class Video(videoID: VideoID, title: String, viewCount: Option[Int] = None)
