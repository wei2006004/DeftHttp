package com.deft.http

import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

const val DEFAULT_CONNECT_TIMEOUT = 3000
const val DEFAULT_READ_TIMEOUT = 3000

enum class Method(val text: String, val doOutput: Boolean = false) {
    GET("GET"), PUT("PUT", true)
}

enum class Result {
    SUCCESS, NETWORK_ERROR, UNKNOW_ERROR
}

data class Request(val method: Method,
                   val url: String,
                   val headers: Map<String, String> = mapOf(),
                   val body: ByteArray = ByteArray(0),
                   val timeout: Int = DEFAULT_CONNECT_TIMEOUT,
                   val readTimeout: Int = DEFAULT_READ_TIMEOUT) {

    class Builder(var method: Method = Method.GET, var url: String? = null) {
        var headers: Map<String, String> = mapOf()
        var body: ByteArray = ByteArray(0)
        var timeout: Int = DEFAULT_CONNECT_TIMEOUT
        var readTimeout: Int = DEFAULT_READ_TIMEOUT

        fun build(): Request {
            return Request(method, url!!, headers, body, timeout, readTimeout)
        }
    }
}

data class Response(val request: Request,
                    val result: Result = Result.UNKNOW_ERROR,
                    val data: ByteArray = ByteArray(0)) {

    class Builder(val request: Request) {
        var result: Result = Result.UNKNOW_ERROR
        var data: ByteArray = ByteArray(0)

        fun build(): Response {
            return Response(request, result, data)
        }
    }
}

private class SyncRequestTask(val request: Request) : Callable<Response> {
    override fun call(): Response {
        return HttpClient.execRequest(request)
    }
}

private class AsyncRequestTask(val request: Request,
                               val success: ((Request, ByteArray) -> Unit)? = null,
                               val failure: ((Result) -> Unit)? = null)
: Callable<Unit> {
    override fun call() {
        val response = HttpClient.execRequest(request)
        if (response.result == Result.SUCCESS) {
            success?.invoke(response.request, response.data)
        } else {
            failure?.invoke(response.result)
        }
    }
}

object HttpClient {
    fun execRequest(request: Request): Response {
        val conn = createConnection(request)
        var result: Result
        var data: ByteArray = ByteArray(0)
        try {
            conn.connect()
            if (conn.errorStream != null) {
                result = Result.NETWORK_ERROR
                data = conn.errorStream?.readBytes() ?: ByteArray(0)
            } else {
                result = Result.SUCCESS
                data = conn.inputStream?.readBytes() ?: ByteArray(0)
            }
        } catch(e: Exception) {
            result = Result.NETWORK_ERROR
        } finally {
            conn.disconnect()
        }

        return Response(request, result, data)
    }

    private fun createConnection(request: Request): HttpURLConnection {
        val url = URL(request.url)
        val conn = url.openConnection() as HttpURLConnection
        conn.apply {
            connectTimeout = request.timeout
            readTimeout = request.readTimeout
            useCaches = false
            requestMethod = request.method.text
            doOutput = request.method.doOutput
            for ((key, value) in request.headers) {
                setRequestProperty(key, value)
            }
        }
        if (request.body.isNotEmpty()) {
            BufferedOutputStream(conn.outputStream).apply {
                write(request.body)
                close()
            }
        }
        return conn
    }
}


object Dhttp {
    val exec: ExecutorService = Executors.newSingleThreadExecutor()

    fun request(request: Request, success: ((Request, ByteArray) -> Unit)?, failure: ((Result) -> Unit)?) {
        exec.submit(AsyncRequestTask(request, success, failure))
    }

    fun requestSync(request: Request): Response {
        val future = exec.submit(SyncRequestTask(request))
        return future.get()
    }
}


