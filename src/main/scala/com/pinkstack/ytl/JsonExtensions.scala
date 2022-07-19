package com.pinkstack.ytl

import io.circe.{ACursor, Decoder, HCursor, Json}

object DownFields:
  def downFields(cursor: ACursor, fields: Seq[String] = Seq.empty): ACursor =
    fields.foldLeft(cursor)((agg, key) => agg.downField(key))

extension (aCursor: ACursor) def downFields(fields: String*) = DownFields.downFields(aCursor, fields.toSeq)

extension (hCursor: HCursor)
  def getTry[A](key: String)(using d: Decoder[A]) =
    hCursor.get[A](key).toTry

extension (json: Json)
  def downField(key: String)      = json.hcursor.downField(key)
  def downFields(fields: String*) = DownFields.downFields(json.hcursor, fields.toSeq)
