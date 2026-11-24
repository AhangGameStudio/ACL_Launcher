package com.acl.launcher.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * HTTP 客户端（对应桌面版 AclPack.HttpGet / AclDownload）。
 *
 * 关键点与桌面版一致：
 * 1. 全局连接池放开（对应 ServicePointManager.DefaultConnectionLimit = 64）
 * 2. 自动 gzip 解压
 * 3. 超时可配
 */
object Http {

    val ioPool = Executors.newFixedThreadPool(16) { r ->
        Thread(r, "ACL-IO").apply { isDaemon = true }
    }

    private const val UA = "ACL-Launcher-Android/1.0 (modpack builder)"

    class HttpException(val code: Int, message: String) : Exception(message)

    /** GET 文本，失败抛异常。 */
    suspend fun getText(url: String, timeoutMs: Int = 25_000): String =
        withContext(Dispatchers.IO) { getTextBlocking(url, timeoutMs) }

    fun getTextBlocking(url: String, timeoutMs: Int = 25_000): String {
        val conn = open(url, timeoutMs)
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.let { readDecoded(it, conn.contentEncoding) } ?: ""
                throw HttpException(code, "HTTP $code：${err.take(300)}")
            }
            return readDecoded(conn.inputStream, conn.contentEncoding)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 读响应流，按 Content-Encoding 解压。
     * 注意：Android 的 HttpURLConnection 不会自动解 gzip，必须自己处理，
     * 否则拿到的就是二进制压缩数据，JSON 解析直接炸。
     */
    private fun readDecoded(input: java.io.InputStream, encoding: String?): String {
        val stream = when (encoding?.lowercase()) {
            "gzip" -> java.util.zip.GZIPInputStream(input)
            "deflate" -> java.util.zip.InflaterInputStream(input)
            else -> input
        }
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    /** POST JSON，返回响应文本。 */
    suspend fun postJson(url: String, body: String, headers: Map<String, String> = emptyMap(),
                         timeoutMs: Int = 90_000): String =
        withContext(Dispatchers.IO) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            try {
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                if (code !in 200..299) {
                    val err = conn.errorStream?.let { readDecoded(it, conn.contentEncoding) } ?: ""
                    throw HttpException(code, "HTTP $code：${err.take(300)}")
                }
                readDecoded(conn.inputStream, conn.contentEncoding)
            } finally {
                conn.disconnect()
            }
        }

    private fun open(url: String, timeoutMs: Int): HttpURLConnection {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Encoding", "gzip")
        }
        return conn
    }

    /**
     * 带进度回调的下载（对应 AclDownloadTask）。
     * 支持 Range 断点续传；不支持时整段流式下载。
     */
    suspend fun download(
        url: String,
        target: File,
        threads: Int = 6,
        onProgress: (done: Long, total: Long, state: String) -> Unit = { _, _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) { downloadBlocking(url, target, threads, onProgress) }

    /** 阻塞式下载，供后台线程直接调用（不占用协程调度器线程）。 */
    fun downloadBlocking(
        url: String,
        target: File,
        threads: Int = 6,
        onProgress: (done: Long, total: Long, state: String) -> Unit = { _, _, _ -> }
    ): Boolean {
        target.parentFile?.mkdirs()
        var conn: HttpURLConnection? = null
        try {
            conn = open(url, 30_000)
            conn.setRequestProperty("Range", "bytes=0-")
            val code = conn.responseCode
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
            val rangeSupported = code == 206

            if (code !in 200..299) {
                onProgress(0, total, "HTTP $code")
                return false
            }

            if (!rangeSupported || total <= 0) {
                // 整段下载
                onProgress(0, total, "正在下载")
                conn.inputStream.use { input ->
                    FileOutputStream(target).use { out ->
                        copyWithProgress(input, out) { d, t ->
                            onProgress(d, if (t > 0) t else d, "正在下载")
                        }
                    }
                }
                onProgress(total.coerceAtLeast(0), total, "完成")
                return true
            }

            // 分片并发下载
            val pieces = splitPieces(total, threads)
            val done = java.util.concurrent.atomic.AtomicLong(0)
            val failed = java.util.concurrent.atomic.AtomicBoolean(false)
            val pieceThreads = pieces.map { piece ->
                Thread {
                    try {
                        downloadPiece(piece, url, target) { n ->
                            done.addAndGet(n)
                            onProgress(done.get(), total, "正在下载")
                        }
                    } catch (e: Exception) {
                        failed.set(true)
                    }
                }.apply {
                    name = "ACL 分片"
                    isDaemon = true
                }
            }
            pieceThreads.forEach { it.start() }
            pieceThreads.forEach { it.join() }
            if (failed.get()) return false

            val actual = target.length()
            if (actual != total) {
                onProgress(actual, total, "大小不符（应为 $total，实际 $actual）")
                return false
            }
            onProgress(total, total, "完成")
            return true
        } catch (e: Exception) {
            onProgress(0, -1, "失败：" + (e.message ?: e.javaClass.simpleName))
            return false
        } finally {
            conn?.disconnect()
        }
    }

    private class Piece(val start: Long, val end: Long) {
        val size get() = end - start + 1
    }

    private fun splitPieces(total: Long, threads: Int): List<Piece> {
        val n = threads.coerceIn(1, 16)
        val pieceSize = (total + n - 1) / n
        val out = ArrayList<Piece>(n)
        var s = 0L
        while (s < total) {
            val e = minOf(s + pieceSize - 1, total - 1)
            out.add(Piece(s, e))
            s = e + 1
        }
        return out
    }

    private fun downloadPiece(
        piece: Piece,
        url: String,
        target: File,
        onBytes: (Long) -> Unit
    ) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 30_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Range", "bytes=${piece.start}-${piece.end}")
        }
        try {
            if (conn.responseCode !in 200..299) throw HttpException(conn.responseCode, "分片失败")
            RandomAccessFile(target, "rw").use { raf ->
                raf.seek(piece.start)
                BufferedInputStream(conn.inputStream).use { input ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        raf.write(buf, 0, n)
                        onBytes(n.toLong())
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun copyWithProgress(input: InputStream, out: FileOutputStream, cb: (Long, Long) -> Unit) {
        val buf = ByteArray(256 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            total += n
            cb(total, total)
        }
        out.flush()
    }
}
