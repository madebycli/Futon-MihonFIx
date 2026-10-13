// Adapted from Kototoro b2864d65 (Apache-2.0); Futon packages and JUnit 4.
package io.github.landwarderer.futon.mihon

import org.junit.Assert.assertSame
import org.junit.Test
import io.github.landwarderer.futon.core.exceptions.CloudFlareBlockedException
import io.github.landwarderer.futon.core.exceptions.InteractiveActionRequiredException
import io.github.landwarderer.futon.core.model.MangaSource
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

class MihonExceptionUnwrapTest {

    private val source = MangaSource("MIHON_TEST")

    @Test
    fun `cloudflare error re-wrapped by Call await is surfaced`() {
        val blocked = CloudFlareBlockedException("https://img.example.org/1.webp", source)
        // Shape produced by eu.kanade.tachiyomi.network.await(): IOException(e.message, e)
        val wrapped = IOException(blocked.message, blocked)

        assertSame(blocked, unwrapMihonFailure(wrapped))
    }

    @Test
    fun `cloudflare error under runtime and io wrappers is surfaced`() {
        val blocked = CloudFlareBlockedException("https://example.org/", source)
        val wrapped = RuntimeException(IOException(blocked.message, blocked))

        assertSame(blocked, unwrapMihonFailure(wrapped))
    }

    @Test
    fun `interactive action is surfaced from the cause chain`() {
        val action = InteractiveActionRequiredException(source, "https://example.org/login")

        assertSame(action, unwrapMihonFailure(RuntimeException(action)))
    }

    @Test
    fun `runtime wrapper around a plain io error yields the io error`() {
        val io = IOException("timeout")

        assertSame(io, unwrapMihonFailure(RuntimeException(io)))
    }

    @Test
    fun `plain io error is kept with its own message`() {
        val io = IOException("HTTP error 429", IOException("inner"))

        assertSame(io, unwrapMihonFailure(io))
    }

    @Test
    fun `cancellation is never replaced`() {
        val cancellation = CancellationException("cancelled").apply {
            initCause(CloudFlareBlockedException("https://example.org/", source))
        }

        assertSame(cancellation, unwrapMihonFailure(cancellation))
    }
}
