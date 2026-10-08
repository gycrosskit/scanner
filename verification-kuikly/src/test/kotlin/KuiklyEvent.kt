package com.tencent.kuikly.core.base.event
open class Event {val callbacks=mutableMapOf<String,(Any?)->Unit>();fun register(name:String,callback:(Any?)->Unit){callbacks[name]=callback}}
