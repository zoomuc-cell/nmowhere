package com.auratechnologies.nowhere

import android.content.Context

/**
 * Leaflet 1.9.4 (BSD-2-Clause, see assets/leaflet/LICENSE.txt) ships inside the app,
 * so the map pages no longer depend on a CDN (unpkg.com) being reachable.
 *
 * The map *tiles* are still fetched from OpenStreetMap, so an internet connection is
 * still needed to draw the background map.
 */
object LeafletAssets {

    @Volatile
    private var cached: String? = null

    /**
     * `<style>` and `<script>` tags for a page's `<head>`. They are inlined into the page,
     * so it does not matter which base URL the page is loaded with.
     */
    fun head(context: Context): String =
        cached ?: synchronized(this) {
            cached ?: build(context.applicationContext).also { cached = it }
        }

    private fun read(context: Context, name: String): String =
        context.assets.open("leaflet/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun build(context: Context): String {
        val css = read(context, "leaflet.css")
        val js = read(context, "leaflet.js")
        val icons = read(context, "leaflet-icons.js") // default marker icons (data URIs)
        return "<style>$css</style>\n<script>$js</script>\n<script>$icons</script>"
    }
}
