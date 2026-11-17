// Ported from Kototoro (Kototoro-app/Kototoro, Apache-2.0) at f32242d649471627d8906a523511bc69217897eb:
// core-cloudflare/src/main/kotlin/org/skepsun/kototoro/core/network/webview/CloudFlareDetection.kt (CF_STATE_JS renamed CF_PAGE_STATE_JS: Futon's CF_STATE_JS is the existing activity solver script)
package io.github.landwarderer.futon.core.network.webview

/**
 * The page-state script ([CF_PAGE_STATE_JS]) returns one of:
 * - "normal": the real page is shown
 * - "loading": still loading or unknown
 * - "managed": a non-interactive Cloudflare challenge (usually passes by itself)
 * - "interactive": a checkbox challenge that needs a person
 * - "hard_block": explicitly blocked
 */
public enum class CloudFlarePageState {
    NORMAL,
    LOADING,
    MANAGED_CHALLENGE,
    INTERACTIVE_CHALLENGE,
    HARD_BLOCK,
}

public class BrowserDocumentReadinessTracker(
    private val quietWindowMs: Long,
) {
    private var stableSince: Long? = null
    private var lastResourceCount: Int? = null

    fun observe(
        pageState: CloudFlarePageState,
        readyState: String,
        url: String,
        resourceCount: Int,
        nowMs: Long,
    ): Boolean {
        val resourceChanged = lastResourceCount != null && lastResourceCount != resourceCount
        lastResourceCount = resourceCount
        val isStableCandidate = pageState == CloudFlarePageState.NORMAL &&
            readyState == "complete" &&
            !url.contains("__cf_chl_", ignoreCase = true)
        if (!isStableCandidate || resourceChanged) {
            stableSince = null
            return false
        }
        val since = stableSince ?: nowMs.also { stableSince = it }
        return nowMs - since >= quietWindowMs
    }
}

public const val CF_CLEARANCE_COOKIE = "cf_clearance"

public const val CF_CHALLENGE_SELECTOR =
    "#challenge-running, #challenge-stage, #cf-challenge-running, .cf-browser-verification, " +
        "#turnstile-wrapper, .cf-turnstile, #cf-please-wait, #challenge-form, " +
        "iframe[src*='challenges.cloudflare.com'], iframe[title*='Cloudflare'], " +
        "input[name='cf-turnstile-response']"

public fun parseCloudFlarePageState(raw: String?): CloudFlarePageState = when (raw?.removeSurrounding("\"")) {
    "normal" -> CloudFlarePageState.NORMAL
    "hard_block" -> CloudFlarePageState.HARD_BLOCK
    "interactive" -> CloudFlarePageState.INTERACTIVE_CHALLENGE
    "managed" -> CloudFlarePageState.MANAGED_CHALLENGE
    else -> CloudFlarePageState.LOADING
}

public const val CF_PAGE_STATE_JS = """
	(function(){
		try {
			var href = (document.location && document.location.href) || '';
			if (href === '' || href === 'about:blank') return 'loading';
			if (document.readyState !== 'interactive' && document.readyState !== 'complete') return 'loading';
			var t = (document.title || '').toLowerCase();
			if (t.indexOf('attention required') !== -1 || t.indexOf('access denied') !== -1) return 'hard_block';
			var challenge = document.querySelector('#challenge-running, #challenge-stage, #cf-challenge-running, ' +
				'.cf-browser-verification, #turnstile-wrapper, .cf-turnstile, #cf-please-wait, #challenge-form');
			if (challenge) {
				// A checkbox (interactive) challenge is recognised by
				// 1) the widget iframe titled "Widget containing a Cloudflare security challenge" (Cloudflare's source), or
				// 2) the hidden cf-turnstile-response input an explicit render creates in the main document.
				// DOM presence is used, not visibility: getBoundingClientRect() is always 0 in a hidden WebView.
				var checkboxWidget = document.querySelector(
					'iframe[title*="Widget containing a Cloudflare security challenge"], input[name="cf-turnstile-response"]'
				);
				if (checkboxWidget) {
					return 'interactive';
				}
				return 'managed';
			}
			if (t.indexOf('just a moment') !== -1 || t.indexOf('un instant') !== -1 ||
				t.indexOf('einen moment') !== -1 || t.indexOf('un momento') !== -1 ||
				t.indexOf('один момент') !== -1 || t.indexOf('请稍候') !== -1 ||
				t.indexOf('請稍候') !== -1) return 'managed';
			if (document.readyState !== 'complete') return 'loading';
			if (!document.body || document.body.children.length === 0) return 'loading';
			return 'normal';
		} catch (e) { return 'loading'; }
	})()
"""
