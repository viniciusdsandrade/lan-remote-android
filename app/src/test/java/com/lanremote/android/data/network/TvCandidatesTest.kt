package com.lanremote.android.data.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Endereços da faixa de documentação (RFC 5737): o repositório é público. */
class TvCandidatesTest {

    private val tv = DiscoveredTV(ip = "192.0.2.17", port = 8080, name = "TV LG 42LN5700", model = "42LN5700-SA_11")
    private val otherTv = DiscoveredTV(ip = "192.0.2.30", port = 8080, name = "TV LG 32LN5400", model = "32LN5400-SB_02")

    @Test
    fun onlyAddressesThatIntroduceThemselvesAsLgTvsAreKept() = runBlocking {
        // A porta 8080 também está aberta num computador com um servidor: sem a descrição UDAP, fica de fora.
        val models = mapOf("192.0.2.17" to "42LN5700-SA_11")
        val found = TvCandidates.confirm(listOf("192.0.2.17", "192.0.2.35", "192.0.2.17"), 8080) { host, _ -> models[host] }

        assertEquals(listOf(tv), found)
    }

    @Test
    fun theAutomaticSearchKeepsWhoAnswersRoapWithoutAskingForTheModel() = runBlocking {
        // Sem a descrição UDAP: fica quem responde à API ROAP, e o modelo o app lê depois.
        val found = TvCandidates.confirmAnswering(listOf("192.0.2.17", "192.0.2.35", "192.0.2.17"), 8080) { host, _ ->
            host == "192.0.2.17"
        }

        assertEquals(listOf(DiscoveredTV(ip = "192.0.2.17", port = 8080, name = "TV LG")), found)
        assertNull(found.single().model)
    }

    @Test
    fun theNameShowsTheModelWithoutTheRegionalSuffix() {
        assertEquals("42LN5700", TvCandidates.shortModel("42LN5700-SA_11"))
        assertEquals("42LN5700", TvCandidates.shortModel("42LN5700"))
        assertEquals("-X", TvCandidates.shortModel("-X"))
    }

    @Test
    fun theTvStillAtTheSavedAddressWins() {
        assertEquals(otherTv, TvCandidates.choose(listOf(tv, otherTv), currentHost = "192.0.2.30", currentModel = null))
    }

    @Test
    fun aSingleTvOnTheNetworkIsTheOne() {
        assertEquals(tv, TvCandidates.choose(listOf(tv), currentHost = "", currentModel = null))
        assertEquals(tv, TvCandidates.choose(listOf(tv), currentHost = "192.0.2.99", currentModel = "outro"))
    }

    @Test
    fun withSeveralTvsOnlyTheSameModelIsPickedAndOtherwiseNone() {
        assertEquals(tv, TvCandidates.choose(listOf(tv, otherTv), currentHost = "192.0.2.99", currentModel = "42LN5700-SA_11"))
        assertNull(TvCandidates.choose(listOf(tv, otherTv), currentHost = "", currentModel = "LG Smart TV (NetCast)"))
        assertNull(TvCandidates.choose(listOf(tv, tv.copy(ip = "192.0.2.18")), currentHost = "", currentModel = "42LN5700-SA_11"))
        assertNull(TvCandidates.choose(emptyList(), currentHost = "192.0.2.17", currentModel = null))
    }
}
