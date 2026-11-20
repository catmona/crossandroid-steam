package de.radicalfishgames.crosscode.steam

import `in`.dragonbra.javasteam.base.ClientMsgProtobuf
import `in`.dragonbra.javasteam.base.IPacketMsg
import `in`.dragonbra.javasteam.enums.EMsg
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverUserstats.CMsgClientGetUserStats
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverUserstats.CMsgClientGetUserStatsResponse
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverUserstats.CMsgClientStoreUserStats2
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverUserstats.CMsgClientStoreUserStatsResponse
import `in`.dragonbra.javasteam.steam.handlers.ClientMsgHandler
import `in`.dragonbra.javasteam.types.KeyValue
import kotlinx.coroutines.CompletableDeferred
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads and writes a user's stats for one game over the CM connection.
 * JavaSteam's own SteamUserStats only covers leaderboards, so this handles
 * ClientGetUserStats and ClientStoreUserStats2 itself. Responses are matched by game id;
 * only one request of each kind per game is in flight at a time.
 */
class UserStatsHandler : ClientMsgHandler() {
    private val pendingGet = ConcurrentHashMap<Long, CompletableDeferred<CMsgClientGetUserStatsResponse>>()
    private val pendingStore = ConcurrentHashMap<Long, CompletableDeferred<CMsgClientStoreUserStatsResponse>>()

    fun getUserStats(
        appId: Int,
        steamId: Long,
    ): CompletableDeferred<CMsgClientGetUserStatsResponse> {
        val result = CompletableDeferred<CMsgClientGetUserStatsResponse>()
        pendingGet.put(appId.toLong(), result)?.cancel()

        val msg =
            ClientMsgProtobuf<CMsgClientGetUserStats.Builder>(CMsgClientGetUserStats::class.java, EMsg.ClientGetUserStats).apply {
                sourceJobID = client.getNextJobID()
                protoHeader.routingAppid = appId
                body.gameId = appId.toLong()
                body.steamIdForUser = steamId
            }
        client.send(msg)
        return result
    }

    fun storeUserStats(
        appId: Int,
        steamId: Long,
        crcStats: Int,
        stats: Map<Int, Int>,
    ): CompletableDeferred<CMsgClientStoreUserStatsResponse> {
        val result = CompletableDeferred<CMsgClientStoreUserStatsResponse>()
        pendingStore.put(appId.toLong(), result)?.cancel()

        val msg =
            ClientMsgProtobuf<CMsgClientStoreUserStats2.Builder>(CMsgClientStoreUserStats2::class.java, EMsg.ClientStoreUserStats2).apply {
                sourceJobID = client.getNextJobID()
                protoHeader.routingAppid = appId
                body.gameId = appId.toLong()
                body.settorSteamId = steamId
                body.setteeSteamId = steamId
                body.crcStats = crcStats
                body.explicitReset = false
                for ((id, value) in stats) {
                    body.addStats(CMsgClientStoreUserStats2.Stats.newBuilder().setStatId(id).setStatValue(value))
                }
            }
        client.send(msg)
        return result
    }

    override fun handleMsg(packetMsg: IPacketMsg) {
        when (packetMsg.msgType) {
            EMsg.ClientGetUserStatsResponse -> {
                val body =
                    ClientMsgProtobuf<CMsgClientGetUserStatsResponse.Builder>(CMsgClientGetUserStatsResponse::class.java, packetMsg)
                        .body
                        .build()
                pendingGet.remove(body.gameId)?.complete(body)
            }
            EMsg.ClientStoreUserStatsResponse -> {
                val body =
                    ClientMsgProtobuf<CMsgClientStoreUserStatsResponse.Builder>(CMsgClientStoreUserStatsResponse::class.java, packetMsg)
                        .body
                        .build()
                pendingStore.remove(body.gameId)?.complete(body)
            }
            else -> Unit
        }
    }

    /** Fails every request still waiting, e.g. after a disconnect. */
    fun cancelAll() {
        pendingGet.values.forEach { it.cancel() }
        pendingGet.clear()
        pendingStore.values.forEach { it.cancel() }
        pendingStore.clear()
    }

    /**
     * Where one achievement lives: bit [bit] of the stat [statId]. [displayName], [description] and
     * [icon] (a file name on Steam's CDN, see [iconUrl]) come from the schema's English "display" block.
     */
    data class AchievementSlot(
        val statId: Int,
        val bit: Int,
        val displayName: String? = null,
        val description: String? = null,
        val icon: String? = null,
    ) {
        fun isSetIn(values: Map<Int, Int>): Boolean = (values[statId] ?: 0) and (1 shl bit) != 0

        fun iconUrl(appId: Int): String? = icon?.let { "https://cdn.akamai.steamstatic.com/steamcommunity/public/images/apps/$appId/$it" }
    }

    companion object {
        /**
         * Maps achievement API names to their stat bit, from the binary KeyValues schema in a
         * GetUserStats response. Achievement stats have a "bits" block whose children are
         * named by bit index and carry the achievement's "name" and a "display" block.
         */
        fun parseSchema(schema: ByteArray): Map<String, AchievementSlot> {
            val root = KeyValue()
            // A truncated or garbled schema throws (EOFException) rather than returning false
            val ok = try { root.tryReadAsBinary(ByteArrayInputStream(schema)) } catch (_: java.io.IOException) { false }
            if (!ok) return emptyMap()

            // The stats block sits under the root, or one level down under a node named after the app.
            val stats =
                root["stats"].takeIf { it != KeyValue.INVALID }
                    ?: root.children.firstNotNullOfOrNull { child -> child["stats"].takeIf { it != KeyValue.INVALID } }
                    ?: return emptyMap()

            val slots = HashMap<String, AchievementSlot>()
            for (stat in stats.children) {
                val statId = stat.name?.toIntOrNull() ?: continue
                val bits = stat["bits"]
                if (bits == KeyValue.INVALID) continue
                for (bitNode in bits.children) {
                    val bit = bitNode.name?.toIntOrNull() ?: continue
                    val name = bitNode["name"].value ?: continue
                    val display = bitNode["display"]
                    slots[name] =
                        AchievementSlot(
                            statId = statId,
                            bit = bit,
                            displayName = localized(display["name"]),
                            description = localized(display["desc"]),
                            icon = display["icon"].value?.takeIf { it.isNotBlank() },
                        )
                }
            }
            return slots
        }

        /** A display string is either a plain value or a block of languages; prefer English. */
        private fun localized(node: KeyValue): String? {
            if (node == KeyValue.INVALID) return null
            node.value?.takeIf { it.isNotBlank() }?.let { return it }
            val english = node["english"].value
            if (!english.isNullOrBlank()) return english
            return node.children.firstNotNullOfOrNull { it.value?.takeIf { v -> v.isNotBlank() } }
        }
    }
}
