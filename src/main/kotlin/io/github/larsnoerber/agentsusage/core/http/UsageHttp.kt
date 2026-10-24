package io.github.larsnoerber.agentsusage.core.http

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.CompletionStage
import java.util.concurrent.Flow
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Bounded, cancellable quota reads. Redirects, cookie storage, and response logging are disabled. */
internal class UsageHttp : AutoCloseable {
    private val closed = AtomicBoolean()
    private val active = AtomicReference<CompletableFuture<HttpResponse<String>>?>()
    private var retryAtNanos = 0L
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER).build()

    fun get(url: String, headers: Map<String, String>): JsonObject = request(url, headers, false)
    fun post(url: String, headers: Map<String, String>): JsonObject = request(url, headers, true)

    private fun request(url: String, headers: Map<String, String>, post: Boolean): JsonObject {
        check(!closed.get())
        if (System.nanoTime() < retryAtNanos) throw UsageHttpFailure(429)
        val uri = URI.create(url)
        require(uri.scheme == "https")
        val request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).header("Accept", "application/json")
        headers.forEach { (key, value) -> request.header(key, value) }
        if (post) request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}"))
        else request.GET()
        val future = client.sendAsync(request.build()) { LimitedJsonBody() }
        active.set(future)
        try {
            if (closed.get()) {
                future.cancel(true)
                error("Usage reader disposed")
            }
            val response = future.get(16, TimeUnit.SECONDS)
            if (response.statusCode() == 429) {
                val seconds = response.headers().firstValue("Retry-After").orElse("").toLongOrNull()
                    ?.coerceIn(180, 3600) ?: 180
                retryAtNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
            }
            if (response.statusCode() != 200) throw UsageHttpFailure(response.statusCode())
            return JsonParser.parseString(response.body()).asJsonObject
        } finally {
            active.compareAndSet(future, null)
            future.cancel(true)
        }
    }

    override fun close() {
        closed.set(true)
        active.getAndSet(null)?.cancel(true)
        client.shutdownNow()
    }
}

/** Enforce the limit while receiving bytes, including chunked responses without Content-Length. */
private class LimitedJsonBody : HttpResponse.BodySubscriber<String> {
    private val delegate = HttpResponse.BodySubscribers.ofString(Charsets.UTF_8)
    private lateinit var subscription: Flow.Subscription
    private var received = 0L
    private var done = false

    override fun getBody(): CompletionStage<String> = delegate.body

    override fun onSubscribe(subscription: Flow.Subscription) {
        this.subscription = subscription
        delegate.onSubscribe(subscription)
    }

    override fun onNext(items: List<ByteBuffer>) {
        if (done) return
        received += items.sumOf { it.remaining().toLong() }
        if (received > 2 * 1024 * 1024) {
            done = true
            subscription.cancel()
            delegate.onError(IOException("Quota response exceeds the 2 MiB limit"))
        } else delegate.onNext(items)
    }

    override fun onError(error: Throwable) {
        if (!done) {
            done = true
            delegate.onError(error)
        }
    }

    override fun onComplete() {
        if (!done) {
            done = true
            delegate.onComplete()
        }
    }
}

internal class UsageHttpFailure(val status: Int) : RuntimeException("Quota request failed (HTTP $status)")
