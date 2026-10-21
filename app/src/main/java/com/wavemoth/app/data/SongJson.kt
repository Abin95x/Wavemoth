package com.wavemoth.app.data

import org.json.JSONObject

fun Song.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("title", title)
    .put("artist", artist)
    .put("duration", durationSec)
    .put("thumbnail", thumbnail)

fun songFromJson(o: JSONObject) = Song(
    id = o.getString("id"),
    title = o.optString("title"),
    artist = o.optString("artist"),
    durationSec = o.optLong("duration"),
    thumbnail = o.optString("thumbnail"),
)
