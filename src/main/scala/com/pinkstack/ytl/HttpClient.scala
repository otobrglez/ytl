package com.pinkstack.loglog

import io.netty.handler.codec.http.cookie.Cookie
import org.asynchttpclient.Dsl.config as asyncHttpClientConfig
import org.asynchttpclient.Dsl.asyncHttpClient
import org.asynchttpclient.cookie.CookieStore
import org.asynchttpclient.uri.Uri
import org.asynchttpclient.{AsyncHttpClient, AsyncHttpClientConfig, Request, Response}
import zio.ZIO.{acquireRelease, attempt, fromFuture, fromFutureJava, logInfo, serviceWithZIO, succeed}
import zio.{RIO, Task, UIO, URIO, ZIO, ZLayer}
import collection.convert.ImplicitConversions.*
import collection.convert.ImplicitConversionsToJava.*

import java.util
import java.util.function.Predicate

case class HttpClientConfig(connectTimeout: Int, readTimeout: Int)

trait HttpClient:
  def execute(request: Request): Task[String]
  def close(): UIO[Unit]

object HttpClient:
  def execute(request: Request): RIO[HttpClient, String] = serviceWithZIO[HttpClient](_.execute(request))
  def close(): URIO[HttpClient, Unit]                    = serviceWithZIO[HttpClient](_.close())

case class HttpClientLive(asyncHttpClient: AsyncHttpClient) extends HttpClient:
  def execute(request: Request): Task[String] =
    fromFutureJava(asyncHttpClient.executeRequest(request)).map(_.getResponseBody)

  def close(): UIO[Unit] =
    attempt(asyncHttpClient.close()).orDie <* logInfo("AsyncHttpClient closed.")



object HttpClientLive:
  class MyCookieStore extends CookieStore:
    def add(uri: Uri, cookie: Cookie): Unit           = ()
    def get(uri: Uri): util.List[Cookie]              = List.empty
    def getAll: util.List[Cookie]                     = List.empty
    def remove(predicate: Predicate[Cookie]): Boolean = true
    def clear(): Boolean                              = true
    def evictExpired(): Unit                          = ()
    def incrementAndGet(): Int                        = 0
    def decrementAndGet(): Int                        = 0
    def count(): Int                                  = 0
    
  private def mkClient(config: HttpClientConfig): ZIO[zio.Scope, Throwable, HttpClientLive] =
    val client = asyncHttpClient(
      asyncHttpClientConfig()
        .setConnectTimeout(config.connectTimeout)
        .setReadTimeout(config.readTimeout)
        .setFollowRedirect(true)
        .setUserAgent("Mozilla/5.0")
        .setCookieStore(new MyCookieStore)
        .build()
    )
    acquireRelease {
      attempt(HttpClientLive(client)) <* logInfo("HttpClient booted.")
    }(_.close())

  def live(config: HttpClientConfig): ZLayer[Any, Throwable, HttpClient] =
    ZLayer.scoped(mkClient(config))
