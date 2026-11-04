package com.example.tvlauncher

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections

// Servidor web minúsculo que corre na box só enquanto o QR Code está no ecrã.
// O telemóvel (na mesma rede) abre a página, envia um ficheiro ou cola um endereço.
// Todos os pedidos exigem o código secreto (token) que vai dentro do QR Code.
class WallpaperServer(
    private val context: Context,
    private val token: String,
    private val listener: Listener
) {

    interface Listener {
        fun onFileReceived(file: File)
        fun onUrlReceived(url: String)
    }

    private var server: ServerSocket? = null

    @Volatile
    private var running = false

    fun start(): Int {
        val s = ServerSocket(0)
        server = s
        running = true
        Thread { acceptLoop(s) }.start()
        return s.localPort
    }

    fun stop() {
        running = false
        try {
            server?.close()
        } catch (e: Exception) {
            // já estava fechado
        }
    }

    private fun acceptLoop(s: ServerSocket) {
        while (running) {
            val client = try {
                s.accept()
            } catch (e: Exception) {
                break
            }
            Thread { handle(client) }.start()
        }
    }

    private fun handle(socket: Socket) {
        try {
            socket.soTimeout = 30000
            socket.use { sock ->
                val input = BufferedInputStream(sock.getInputStream())
                val out = sock.getOutputStream()
                val head = readHead(input) ?: return
                val lines = head.split("\r\n")
                val first = lines[0].split(" ")
                if (first.size < 2) return
                val method = first[0]
                val uri = Uri.parse("http://box" + first[1])
                val path = uri.path ?: "/"

                val headers = HashMap<String, String>()
                for (i in 1 until lines.size) {
                    val idx = lines[i].indexOf(':')
                    if (idx > 0) {
                        headers[lines[i].substring(0, idx).trim().lowercase()] =
                            lines[i].substring(idx + 1).trim()
                    }
                }

                if (uri.getQueryParameter("t") != token) {
                    respond(out, 403, "text/plain", "Acesso negado")
                    return
                }

                if (method == "GET" && path == "/") {
                    respond(out, 200, "text/html", PAGE)
                } else if (method == "POST" && path == "/upload") {
                    receiveFile(input, out, headers, uri.getQueryParameter("name") ?: "fundo")
                } else if (method == "POST" && path == "/fetch") {
                    val address = uri.getQueryParameter("url") ?: ""
                    if (address.startsWith("http://") || address.startsWith("https://")) {
                        listener.onUrlReceived(address)
                        respond(out, 200, "text/plain", "ok")
                    } else {
                        respond(out, 400, "text/plain", "Endereço inválido")
                    }
                } else {
                    respond(out, 404, "text/plain", "Não encontrado")
                }
            }
        } catch (e: Exception) {
            // ligação interrompida: ignorar
        }
    }

    private fun receiveFile(
        input: InputStream,
        out: OutputStream,
        headers: Map<String, String>,
        name: String
    ) {
        val length = headers["content-length"]?.toLongOrNull() ?: -1L
        if (length <= 0L || length > WallpaperFiles.MAX_BYTES) {
            respond(out, 413, "text/plain", "Ficheiro vazio ou demasiado grande (máximo 300 MB)")
            return
        }
        val ext = name.substringAfterLast('.', "").lowercase()
        if (!WallpaperFiles.isAllowedExt(ext)) {
            respond(out, 415, "text/plain", "Formato não suportado. Usa JPG, PNG, WEBP, MP4 ou WEBM.")
            return
        }

        val dest = WallpaperFiles.newFile(context, name.substringBeforeLast('.'), ext)
        val tmp = File(dest.parentFile, dest.name + ".part")
        try {
            var remaining = length
            FileOutputStream(tmp).use { fo ->
                val buf = ByteArray(65536)
                while (remaining > 0L) {
                    val want = if (remaining < buf.size) remaining.toInt() else buf.size
                    val n = input.read(buf, 0, want)
                    if (n < 0) throw EOFException()
                    fo.write(buf, 0, n)
                    remaining -= n
                }
            }
            if (!tmp.renameTo(dest)) throw IllegalStateException("não foi possível guardar")
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
        if ((ext == "mp4" || ext == "webm") && !WallpaperFiles.isLandscapeVideo(dest)) {
            dest.delete()
            respond(out, 415, "text/plain", "O vídeo tem de ser horizontal (16:9).")
            return
        }
        listener.onFileReceived(dest)
        respond(out, 200, "text/plain", "ok")
    }

    private fun readHead(input: InputStream): String? {
        val sb = StringBuilder()
        var count = 0
        while (count < 16384) {
            val b = input.read()
            if (b < 0) return null
            sb.append(b.toChar())
            count++
            if (count >= 4 && sb.endsWith("\r\n\r\n")) return sb.toString()
        }
        return null
    }

    private fun respond(out: OutputStream, code: Int, type: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val status = when (code) {
            200 -> "OK"
            400 -> "Bad Request"
            403 -> "Forbidden"
            404 -> "Not Found"
            413 -> "Payload Too Large"
            415 -> "Unsupported Media Type"
            else -> "Error"
        }
        val head = "HTTP/1.1 " + code + " " + status + "\r\n" +
            "Content-Type: " + type + "; charset=utf-8\r\n" +
            "Content-Length: " + bytes.size + "\r\n" +
            "Connection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        out.write(bytes)
        out.flush()
    }

    companion object {

        // Endereço da box na rede local (Ethernet ou Wi-Fi).
        fun localIp(): String? {
            try {
                var fallback: String? = null
                val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
                for (ni in Collections.list(interfaces)) {
                    if (!ni.isUp || ni.isLoopback) continue
                    for (addr in Collections.list(ni.inetAddresses)) {
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            val ip = addr.hostAddress ?: continue
                            if (addr.isSiteLocalAddress) return ip
                            if (fallback == null) fallback = ip
                        }
                    }
                }
                return fallback
            } catch (e: Exception) {
                return null
            }
        }

        private const val PAGE = """<!doctype html>
<html lang="pt"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Fundos da TV</title>
<style>
body{font-family:system-ui,sans-serif;background:#10142a;color:#fff;margin:0;padding:20px}
h1{font-size:22px}
.card{background:#1c2142;border-radius:14px;padding:16px;margin:16px 0}
p{color:#b8bdd9;font-size:14px}
button{background:#6c63ff;color:#fff;border:0;border-radius:10px;padding:12px 16px;font-size:16px;width:100%;margin-top:10px}
input[type=text]{width:100%;box-sizing:border-box;padding:12px;border-radius:10px;border:0;font-size:16px}
input[type=file]{width:100%;margin-top:8px;color:#fff}
#st{margin-top:12px;min-height:22px;font-size:16px}
</style></head><body>
<h1>Fundos de ecrã da TV</h1>
<div class="card"><b>Enviar uma imagem ou um vídeo</b>
<p>Imagens JPG, PNG ou WEBP. Vídeos MP4 ou WEBM, curtos, em 1080p e 16:9 (repetem-se em ciclo).</p>
<input type="file" id="f" accept="image/jpeg,image/png,image/webp,video/mp4,video/webm">
<button id="up">Enviar para a TV</button></div>
<div class="card"><b>Ou colar o endereço de uma imagem ou vídeo</b>
<p>A TV descarrega o ficheiro diretamente da Internet.</p>
<input type="text" id="u" placeholder="https://...">
<button id="go">Descarregar na TV</button></div>
<div id="st"></div>
<script>
var t=new URLSearchParams(location.search).get('t')||'';
var st=document.getElementById('st');
function say(m){st.textContent=m;}
document.getElementById('up').onclick=function(){
  var f=document.getElementById('f').files[0];
  if(!f){say('Escolhe primeiro um ficheiro.');return;}
  var x=new XMLHttpRequest();
  x.open('POST','/upload?t='+encodeURIComponent(t)+'&name='+encodeURIComponent(f.name));
  x.upload.onprogress=function(e){if(e.lengthComputable){say('A enviar... '+Math.round(e.loaded*100/e.total)+'%');}};
  x.onload=function(){say(x.status==200?'Enviado! O fundo ja esta na TV.':'Erro: '+x.responseText);};
  x.onerror=function(){say('Falhou a ligacao a TV.');};
  x.send(f);
};
document.getElementById('go').onclick=function(){
  var u=document.getElementById('u').value.trim();
  if(!u){say('Cola um endereco.');return;}
  var x=new XMLHttpRequest();
  x.open('POST','/fetch?t='+encodeURIComponent(t)+'&url='+encodeURIComponent(u));
  x.onload=function(){say(x.status==200?'Pedido enviado. A TV esta a descarregar.':'Erro: '+x.responseText);};
  x.onerror=function(){say('Falhou a ligacao a TV.');};
  x.send();
};
</script></body></html>"""
    }
}
