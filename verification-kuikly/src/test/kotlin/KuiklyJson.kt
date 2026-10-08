package com.tencent.kuikly.core.nvi.serialization.json
/** JVM JSON/transport double; does not assert the native Kuikly SDK serializer. */
class JSONObject private constructor(private val delegate: org.json.JSONObject) {
 constructor():this(org.json.JSONObject())
 constructor(text:String):this(org.json.JSONObject(text))
 fun put(key:String,value:Any) {delegate.put(key,value)}
 fun opt(key:String):Any?=delegate.opt(key)
 fun optString(key:String)=delegate.optString(key, "")
 fun optDouble(key:String,fallback:Double)=delegate.optDouble(key,fallback)
 fun optLong(key:String,fallback:Long)=delegate.optLong(key,fallback)
 override fun toString()=delegate.toString()
}
