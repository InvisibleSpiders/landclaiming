package com.invisiblespiders.havenclaims.plugin.flag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.invisiblespiders.havenclaims.api.flag.FlagState;
import com.invisiblespiders.havenclaims.plugin.claim.Claim;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimChunk;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimIndex;
import com.invisiblespiders.havenclaims.plugin.claim.OwnerType;
import com.invisiblespiders.havenclaims.plugin.storage.ClaimRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimFlagServiceTest {
    private final UUID owner = UUID.randomUUID();

    private Claim claim(Map<String, FlagState> flags) {
        return new Claim(UUID.randomUUID(), "C", OwnerType.PLAYER, owner, UUID.randomUUID(),
                Set.of(new ClaimChunk(UUID.randomUUID(), 0, 0)), flags, Set.of(), Set.of(),
                Instant.now(), Instant.now());
    }

    private ClaimFlagService service(List<Claim> saved) {
        ClaimRepository repo = new InMemoryRepo(saved);
        return new ClaimFlagService(repo, new ClaimIndex(), FlagRegistry.createDefault());
    }

    @Test
    void cyclePlayerActionAdvancesOffToVisitors() {
        List<Claim> saved = new ArrayList<>();
        Claim c = claim(Map.of("container_access", FlagState.OFF));
        ClaimFlagResult result = service(saved).cycleFlag(owner, c, "container_access", perm -> true);
        assertTrue(result.allowed());
        assertEquals(FlagState.VISITORS, saved.get(saved.size() - 1).flags().get("container_access"));
    }

    @Test
    void cycleWorldEffectSkipsVisitors() {
        List<Claim> saved = new ArrayList<>();
        Claim c = claim(Map.of("explosion_damage", FlagState.OFF));
        service(saved).cycleFlag(owner, c, "explosion_damage", perm -> true);
        assertEquals(FlagState.ALL, saved.get(saved.size() - 1).flags().get("explosion_damage"));
    }

    @Test
    void setVisitorsOnWorldEffectIsRejected() {
        ClaimFlagResult result = service(new ArrayList<>())
                .setFlagState(owner, claim(Map.of()), "explosion_damage", FlagState.VISITORS, perm -> true);
        assertFalse(result.allowed());
    }

    @Test
    void nextStateComputesWithoutWriting() {
        ClaimFlagService service = service(new ArrayList<>());
        assertEquals(FlagState.VISITORS,
                service.nextState(claim(Map.of("container_access", FlagState.OFF)), "container_access"));
        assertEquals(FlagState.ALL,
                service.nextState(claim(Map.of("explosion_damage", FlagState.OFF)), "explosion_damage"));
    }

    @Test
    void nonOwnerCannotSetFlag() {
        ClaimFlagResult result = service(new ArrayList<>())
                .setFlagState(UUID.randomUUID(), claim(Map.of()), "build", FlagState.VISITORS, perm -> true);
        assertFalse(result.allowed());
        assertEquals("claim.flag.not-owner", result.messageKey());
    }

    @Test
    void unknownFlagIsDenied() {
        ClaimFlagResult result = service(new ArrayList<>())
                .setFlagState(owner, claim(Map.of()), "nonexistent_flag", FlagState.OFF, perm -> true);
        assertFalse(result.allowed());
        assertEquals("claim.flag.unknown", result.messageKey());
    }

    @Test
    void missingPermissionIsDenied() {
        ClaimFlagResult result = service(new ArrayList<>())
                .setFlagState(owner, claim(Map.of()), "build", FlagState.VISITORS, perm -> false);
        assertFalse(result.allowed());
        assertEquals("claim.flag.no-permission", result.messageKey());
    }

    @Test
    void setFlagPreservesDeniedPlayers() {
        List<Claim> saved = new ArrayList<>();
        UUID denied = UUID.randomUUID();
        Claim c = new Claim(UUID.randomUUID(), "C", OwnerType.PLAYER, owner, UUID.randomUUID(),
                Set.of(new ClaimChunk(UUID.randomUUID(), 0, 0)),
                Map.of("build", FlagState.VISITORS), Set.of(), Set.of(denied),
                Instant.now(), Instant.now());
        service(saved).setFlagState(owner, c, "build", FlagState.OFF, perm -> true);
        assertEquals(Set.of(denied), saved.get(saved.size() - 1).deniedPlayers());
    }

    // Minimal in-memory ClaimRepository capturing saved claims.
    private static final class InMemoryRepo implements ClaimRepository {
        private final List<Claim> saved;
        InMemoryRepo(List<Claim> saved) { this.saved = saved; }
        @Override public void saveClaim(Claim claim) { saved.add(claim); }
        @Override public void replaceClaims(Claim c, List<UUID> d) { saved.add(c); }
        @Override public void deleteClaim(UUID id) {}
        @Override public Optional<Claim> findClaimAt(UUID w, int x, int z) { return Optional.empty(); }
        @Override public Optional<Claim> findClaimById(UUID id) { return Optional.empty(); }
        @Override public List<Claim> findClaimsByOwner(OwnerType t, UUID o) { return List.of(); }
        @Override public List<Claim> findAllClaims() { return List.of(); }
    }
}
