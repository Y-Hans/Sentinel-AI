package com.sentinel.ai.protection.intent.link

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Authoritative, side-effect-free URL parser and normalizer for click-time link protection.
 *
 * Parsing preserves the heuristic engine's existing missing-scheme policy: when the first
 * [URI] parse has no host, a single `https://` parse fallback is attempted. Normalization trims
 * surrounding whitespace and lowercases only the scheme and host. Path, query, fragment,
 * explicit ports, userinfo, encoding, and trailing-slash semantics are otherwise preserved.
 */
object UrlNormalizer {

    const val MAX_DECODE_DEPTH: Int = 2

    fun normalize(url: String): String = parse(url).normalized

    fun parse(url: String): ParsedUrl {
        val original = url
        val trimmed = url.trim()
        val directUri = parseUri(trimmed)
        val directHasHost = directUri?.host != null
        val fallbackUri = if (directHasHost) null else parseUri("https://$trimmed")
        val parsedUri = directUri?.takeIf { it.host != null } ?: fallbackUri
        val hasSchemeWithAuthority = SCHEME_WITH_AUTHORITY.containsMatchIn(trimmed)
        val schemeWasInferred = !directHasHost && !hasSchemeWithAuthority && parsedUri?.host != null
        val acceptedInputShape = directHasHost || schemeWasInferred

        val originalHost = parsedUri?.host
        val host = originalHost?.lowercase()
        val rawAuthority = parsedUri?.rawAuthority.orEmpty()
        val hasExplicitPort = hasExplicitPort(rawAuthority, originalHost)
        val port = parsedUri?.port?.takeIf { it >= 0 }
        val hasUserInfo = !parsedUri?.rawUserInfo.isNullOrBlank()
        val portIsValid = !hasExplicitPort || port != null && port in MIN_PORT..MAX_PORT
        val authorityIsValid = rawAuthority.count { it == '@' } <= 1
        val isValid = trimmed.isNotEmpty() &&
            acceptedInputShape &&
            originalHost != null &&
            portIsValid &&
            authorityIsValid

        val rawPath = parsedUri?.rawPath.orEmpty()
        val percentDecodedPath = decodeLayers(rawPath, depthLimit = 1).single()
        val rawQuery = parsedUri?.rawQuery
        val parameters = parseQueryParameters(rawQuery)
        val decodedParameters = buildDecodedParameterMap(parameters)
        val ipv4 = originalHost?.let(IPV4_PATTERN::matches) == true
        val ipv6 = originalHost?.let { candidate ->
            candidate.contains(':') && IPV6_PATTERN.matches(candidate)
        } == true

        val normalized = if (acceptedInputShape && originalHost != null) {
            rebuildNormalized(parsedUri, originalHost)
        } else {
            normalizeSchemeOnly(trimmed)
        }

        return ParsedUrl(
            original = original,
            normalized = normalized,
            scheme = parsedUri?.scheme?.lowercase(),
            host = host,
            originalHost = originalHost,
            port = port,
            hasExplicitPort = hasExplicitPort,
            hasUserInfo = hasUserInfo,
            path = parsedUri?.path.orEmpty(),
            rawPath = rawPath,
            percentDecodedPath = percentDecodedPath,
            pathContainsEmbeddedHttpUrl =
                EMBEDDED_HTTP_URL.containsMatchIn(rawPath) ||
                    EMBEDDED_HTTP_URL.containsMatchIn(percentDecodedPath),
            query = rawQuery,
            fragment = parsedUri?.rawFragment,
            isValid = isValid,
            isIpv4 = ipv4,
            isIpv6 = ipv6,
            isPunycode = host?.let { it.startsWith("xn--") || it.contains(".xn--") } == true,
            subdomainCount = originalHost?.count { it == '.' } ?: 0,
            rawQueryParameterCount = rawQuery.orEmpty().split('&').count(String::isNotBlank),
            queryParameters = parameters,
            decodedQueryParameters = decodedParameters,
            schemeWasInferred = schemeWasInferred
        )
    }

    private fun parseUri(value: String): URI? = runCatching { URI(value) }.getOrNull()

    private fun parseQueryParameters(rawQuery: String?): List<ParsedUrlQueryParameter> =
        rawQuery.orEmpty()
            .split('&')
            .mapNotNull { part ->
                val rawName = part.substringBefore('=', missingDelimiterValue = "")
                rawName.takeIf(String::isNotBlank)?.let { name ->
                    val rawValue = part.substringAfter('=', missingDelimiterValue = "")
                    val decodedName = decodeLayers(name, depthLimit = 1).single()
                    val decodedValues = decodeLayers(rawValue, depthLimit = MAX_DECODE_DEPTH)
                    val decodedValue = decodedValues[0]
                    val twiceDecodedValue = decodedValues[1]
                    ParsedUrlQueryParameter(
                        rawName = name,
                        rawValue = rawValue,
                        decodedName = decodedName,
                        decodedValue = decodedValue,
                        twiceDecodedValue = twiceDecodedValue,
                        isPlausibleHttpDestination = isPlausibleHttpDestination(twiceDecodedValue),
                        containsEmbeddedHttpUrl =
                            EMBEDDED_HTTP_URL.containsMatchIn(decodedValue) ||
                                EMBEDDED_HTTP_URL.containsMatchIn(twiceDecodedValue)
                    )
                }
            }

    private fun buildDecodedParameterMap(
        parameters: List<ParsedUrlQueryParameter>
    ): Map<String, List<String>> {
        val valuesByName = linkedMapOf<String, MutableList<String>>()
        parameters.forEach { parameter ->
            valuesByName.getOrPut(parameter.decodedName) { mutableListOf() }
                .add(parameter.decodedValue)
        }
        return valuesByName.mapValues { (_, values) -> values.toList() }
    }

    private fun decodeOnce(value: String): String = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrDefault(value)

    private fun decodeLayers(value: String, depthLimit: Int): List<String> {
        require(depthLimit in 1..MAX_DECODE_DEPTH)
        val decodedValues = ArrayList<String>(depthLimit)
        var current = value
        repeat(depthLimit) {
            current = decodeOnce(current)
            decodedValues.add(current)
        }
        return decodedValues
    }

    private fun isPlausibleHttpDestination(value: String): Boolean =
        parseUri(value)?.let { nested ->
            nested.host != null &&
                (nested.scheme.equals("http", ignoreCase = true) ||
                    nested.scheme.equals("https", ignoreCase = true))
        } == true

    private fun hasExplicitPort(rawAuthority: String, host: String?): Boolean {
        if (rawAuthority.isEmpty() || host == null) return false
        val hostAndPort = rawAuthority.substringAfterLast('@')
        return if (hostAndPort.startsWith('[')) {
            val closingBracket = hostAndPort.indexOf(']')
            closingBracket >= 0 && hostAndPort.drop(closingBracket + 1).startsWith(':')
        } else {
            hostAndPort.lastIndexOf(':') >= 0
        }
    }

    private fun rebuildNormalized(uri: URI, originalHost: String): String {
        val scheme = uri.scheme?.lowercase() ?: return uri.toString()
        val rawAuthority = uri.rawAuthority ?: return normalizeSchemeOnly(uri.toString())
        val hostIndex = rawAuthority.lastIndexOf(originalHost, ignoreCase = true)
        val normalizedAuthority = if (hostIndex >= 0) {
            rawAuthority.replaceRange(
                hostIndex,
                hostIndex + originalHost.length,
                originalHost.lowercase()
            )
        } else {
            rawAuthority
        }

        return buildString {
            append(scheme)
            append("://")
            append(normalizedAuthority)
            append(uri.rawPath.orEmpty())
            uri.rawQuery?.let { append('?').append(it) }
            uri.rawFragment?.let { append('#').append(it) }
        }
    }

    private fun normalizeSchemeOnly(value: String): String {
        val match = SCHEME_PREFIX.find(value) ?: return value
        return match.groupValues[1].lowercase() + value.substring(match.groupValues[1].length)
    }

    private const val MIN_PORT = 0
    private const val MAX_PORT = 65_535
    private val SCHEME_WITH_AUTHORITY = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")
    private val SCHEME_PREFIX = Regex("^([A-Za-z][A-Za-z0-9+.-]*):")
    private val IPV4_PATTERN = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")
    private val IPV6_PATTERN = Regex("""^\[?[0-9a-fA-F:]+]?$""")
    private val EMBEDDED_HTTP_URL = Regex("(?i)https?://[^\\s/?#]+")
}
