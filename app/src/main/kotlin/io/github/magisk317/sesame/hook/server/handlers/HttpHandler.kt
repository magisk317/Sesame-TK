package io.github.magisk317.sesame.hook.server.handlers

import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response

interface HttpHandler {
    fun handle(session: IHTTPSession, body: String? = null): Response
}