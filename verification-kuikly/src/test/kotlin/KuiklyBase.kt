package com.tencent.kuikly.core.base
open class Attr { fun setProp(name: String, value: Any) {} }
abstract class DeclarativeBaseView<A,E> {abstract fun viewName():String;abstract fun createAttr(): A;abstract fun createEvent():E}
open class ViewContainer<A,E> {fun <T> addChild(v: T, init:T.()->Unit) {}}
