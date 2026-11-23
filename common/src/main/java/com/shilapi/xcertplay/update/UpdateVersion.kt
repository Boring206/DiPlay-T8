package com.shilapi.xcertplay.update

internal object UpdateVersion {
    private val TRIPLE = Regex("(\\d+)\\.(\\d+)\\.(\\d+)")

    internal fun parse(value: String): Triple<Int, Int, Int>? {
        // tag_name comes from the GitHub API. A field wider than Int would throw in toInt().
        val match = TRIPLE.find(value) ?: return null
        val major = match.groupValues[1].toIntOrNull() ?: return null
        val minor = match.groupValues[2].toIntOrNull() ?: return null
        val patch = match.groupValues[3].toIntOrNull() ?: return null
        return Triple(major, minor, patch)
    }

    internal fun isNewer(remote: String, installed: String): Boolean {
        val target = parse(remote) ?: return false
        val current = parse(installed) ?: return true
        if (target.first != current.first) return target.first > current.first
        if (target.second != current.second) return target.second > current.second
        return target.third > current.third
    }
}
