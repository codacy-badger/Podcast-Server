package com.github.davinkevin.podcastserver.extension.java.util

import java.util.*

/**
 * Created by kevin on 02/11/2019
 */
fun <T> Optional<T>.orNull(): T? = this.orElse(null)
fun <T> tryOrNull(f: () -> T?): T? = try { f() } catch (e: Exception) { null }
