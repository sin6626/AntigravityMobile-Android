package com.antigravity.mobile.data.service

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** 在 Logcat 中记录每次网关调用；凭据字段只记录名称，不输出原值。 */
class ApiTraceInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val params = JSONObject()
        request.url.queryParameterNames.forEach { name ->
            params.put(name, if (isSecret(name)) "[已隐藏]" else request.url.queryParameterValues(name).joinToString(","))
        }
        val body = Buffer()
        request.body?.writeTo(body)
        if (body.size > 0L && body.size <= 128 * 1024L) {
            val raw = body.readUtf8()
            params.put("body", sanitize(parseJson(raw)))
        }
        val headers = JSONObject()
        request.headers.names().forEach { name ->
            headers.put(name, if (isSecret(name)) "[已隐藏]" else request.header(name))
        }

        return try {
            val response = chain.proceed(request)
            try {
                val responseData = if (response.code == 101) {
                    JSONObject().put("websocket", "connected")
                } else {
                    sanitize(parseJson(response.peekBody(128 * 1024L).string()))
                }
                val result = JSONObject()
                    .put("statusCode", response.code)
                    .put("method", request.method)
                    .put("url", request.url.toString())
                    .put("headers", headers)
                    .put("params", params)
                    .put("response", responseData)
                Log.d("API_TRACE", result.toString())
            } catch (logError: Exception) {
                Log.w("API_TRACE", "日志格式化失败: ${logError.message}")
            }
            response
        } catch (error: Exception) {
            Log.e("API_TRACE", JSONObject()
                .put("statusCode", JSONObject.NULL)
                .put("method", request.method)
                .put("url", request.url.toString())
                .put("headers", headers)
                .put("params", params)
                .put("response", JSONObject().put("error", error.message ?: "网络请求失败"))
                .toString())
            throw error
        }
    }

    private fun parseJson(raw: String): Any = try {
        JSONTokener(raw).nextValue() ?: raw
    } catch (_: Exception) {
        raw
    }

    private fun sanitize(value: Any?): Any? = when (value) {
        is JSONObject -> JSONObject().also { target ->
            value.keys().forEach { key ->
                target.put(key, if (isSecret(key)) "[已隐藏]" else sanitize(value.opt(key)))
            }
        }
        is JSONArray -> JSONArray().also { target ->
            for (index in 0 until value.length()) target.put(sanitize(value.opt(index)))
        }
        else -> value
    }

    private fun isSecret(name: String): Boolean = name.lowercase()
        .replace("-", "")
        .replace("_", "") in setOf(
            "authorization", "xdevicetoken", "devicetoken", "pairingcode", "token",
            "base64data", "inlinedata", "ticket",
        )
}
