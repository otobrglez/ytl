package com.pinkstack.ytl

import zio.*
import zio.ZIO.{acquireReleaseWith, attempt, attemptBlocking}

object Resources:
  private def resourceFrom(resourceName: String) =
    attemptBlocking(scala.io.Source.fromURL(ClassLoader.getSystemResource(resourceName)))

  def read(resourceName: String): Task[String] =
    acquireReleaseWith(resourceFrom(resourceName))(source => attempt(source.close()).orDie) { source =>
      attemptBlocking(source.getLines().mkString("\n"))
    }
