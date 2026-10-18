package com.infinitezerone.minibgm.core.network.ech

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EchFailureCooldownTest {
    @Test
    fun mark_keeps_host_cooling_down_until_ttl_expires() {
        var now = 1_000L
        val cooldown = EchFailureCooldown(ttlMillis = 500, nowMillis = { now })

        assertFalse(cooldown.isCoolingDown("acg.rip"))
        assertTrue(cooldown.mark("ACG.RIP"))
        assertTrue(cooldown.isCoolingDown("acg.rip"))
        assertTrue(cooldown.isCoolingDown("  acg.rip "))

        now += 499
        assertTrue(cooldown.isCoolingDown("acg.rip"))

        now += 2
        assertFalse(cooldown.isCoolingDown("acg.rip"))
    }

    @Test
    fun repeated_failure_while_cooling_is_not_a_new_round() {
        var now = 0L
        val cooldown = EchFailureCooldown(ttlMillis = 1_000, nowMillis = { now })

        assertTrue(cooldown.mark("origin.example"))
        now += 100
        assertFalse(cooldown.mark("origin.example"))

        now += 1_000
        assertTrue(cooldown.mark("origin.example"))
        assertTrue(cooldown.isCoolingDown("origin.example"))
    }

    @Test
    fun subdomains_share_the_parent_registration_cooldown() {
        var now = 0L
        val cooldown = EchFailureCooldown(ttlMillis = 1_000, nowMillis = { now })

        cooldown.mark("acg.rip")

        assertTrue(cooldown.isCoolingDown("img.acg.rip"))
        assertFalse(cooldown.isCoolingDown("notacg.rip"))
        assertFalse(cooldown.isCoolingDown("acg.rip.evil.com"))
    }

    @Test
    fun hosts_are_independent_and_blank_is_ignored() {
        var now = 0L
        val cooldown = EchFailureCooldown(ttlMillis = 1_000, nowMillis = { now })

        assertTrue(cooldown.mark("a.example"))
        assertFalse(cooldown.isCoolingDown("b.example"))
        assertFalse(cooldown.mark("   "))
    }
}
