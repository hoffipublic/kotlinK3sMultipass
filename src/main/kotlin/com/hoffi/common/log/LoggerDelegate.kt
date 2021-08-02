package com.hoffi.common.log

import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import kotlin.reflect.full.companionObject

class LoggerDelegate<in R : Any> : ReadOnlyProperty<R, Logger> {
    override fun getValue(thisRef: R, property: KProperty<*>)
            = getLogger(getClassForLogging(thisRef.javaClass))

    @Suppress("NOTHING_TO_INLINE")
    inline fun <T : Any> getClassForLogging(javaClass: Class<T>): Class<*> {
        return javaClass.enclosingClass?.takeIf {
            // if logging inside companion take the enclosingClass (and not the companion itself)
            // uses reflection!
            it.kotlin.companionObject?.java == javaClass
        } ?: javaClass
    }
}
