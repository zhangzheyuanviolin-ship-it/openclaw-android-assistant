package com.codex.mobile

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject

class ShizukuShellBridgeServer(
    private val context: Context,
    port: Int = BRIDGE_PORT,
) : NanoHTTPD("127.0.0.1", port) {

    companion object {
        private const val TAG = "ShizukuBridgeServer"
        const val BRIDGE_PORT = 18926
    }

    override fun serve(session: IHTTPSession): Response {
        return try {
            when {
                session.method == Method.GET && session.uri == "/status" -> handleStatus()
                session.method == Method.POST && session.uri == "/enable" -> handleEnable(true)
                session.method == Method.POST && session.uri == "/disable" -> handleEnable(false)
                session.method == Method.POST && session.uri == "/exec" -> handleExec(session)
                else -> jsonResponse(
                    Response.Status.NOT_FOUND,
                    JSONObject().put("ok", false).put("error", "Not found"),
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "serve failed", e)
            jsonResponse(
                Response.Status.INTERNAL_ERROR,
                JSONObject().put("ok", false).put("error", e.message ?: "Internal error"),
            )
        }
    }

    private fun handleStatus(): Response {
        val installed = ShizukuController.isShizukuAppInstalled(context)
        val running = ShizukuController.isServiceRunning()
        val granted = ShizukuController.hasPermission()
        val enabled = ShizukuController.isBridgeEnabled(context)

        val body = JSONObject()
            .put("ok", true)
            .put("installed", installed)
            .put("running", running)
            .put("granted", granted)
            .put("enabled", enabled)
        return jsonResponse(Response.Status.OK, body)
    }

    private fun handleEnable(enabled: Boolean): Response {
        ShizukuController.setBridgeEnabled(context, enabled)
        val body = JSONObject()
            .put("ok", true)
            .put("enabled", enabled)
        return jsonResponse(Response.Status.OK, body)
    }

    private fun handleExec(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val raw = files["postData"] ?: ""
        val payload = if (raw.isBlank()) JSONObject() else JSONObject(raw)
        val command = payload.optString("command", "").trim()
        if (command.isEmpty()) {
            return jsonResponse(
                Response.Status.BAD_REQUEST,
                JSONObject().put("ok", false).put("error", "Missing command"),
            )
        }

        if (!ShizukuController.isBridgeEnabled(context)) {
            return jsonResponse(
                Response.Status.FORBIDDEN,
                JSONObject()
                    .put("ok", false)
                    .put("error", "Shizuku bridge is disabled in permission center"),
            )
        }

        val result = ShizukuController.executeShellCommand(command)
        val body = JSONObject()
            .put("ok", result.success)
            .put("success", result.success)
            .put("exitCode", result.exitCode)
            .put("stdout", result.stdout)
            .put("stderr", result.stderr)

        if (result.error != null) {
            body.put("error", result.error)
        }

        return jsonResponse(Response.Status.OK, body)
    }

    private fun jsonResponse(status: Response.Status, json: JSONObject): Response {
        return newFixedLengthResponse(status, "application/json; charset=utf-8", json.toString())
    }
}
