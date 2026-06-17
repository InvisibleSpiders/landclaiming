# DB-Backed Claim Limits — Phase B (HavenVault) Implementation Plan

**Plan status:** Historical cross-repo execution artifact. The unchecked boxes below are preserved for context and should not be treated as the current active HavenClaims backlog.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a chunk-limit upgrade purchase flow to HavenVault: players buy extra claim chunks via `/upgrades` (exponential pricing, bulk discounts, configurable currency); admins adjust limits with `/hvault admin claim limit`.

**Architecture:** A new `claim` package holds `ClaimUpgradeConfig`, `ClaimChunkUpgradeService`, `ClaimChunkLimitEffect`, and `PurchaseResult`. `UpgradesDialog` gains a claim section. `HavenVaultPlugin` soft-loads `HavenClaimsLimitService` on enable; all claim behaviour is silent no-op when HavenClaims is absent.

**Tech Stack:** Java 25, Paper API 26.1.2, HavenCore `HavenEconomyService`, HavenClaims API `HavenClaimsLimitService` (`com.nick:havenclaims-api:1.7.0-SNAPSHOT`, compileOnly soft-dep). JUnit 5 + Mockito.

**Prerequisite:** Phase A (HavenClaims) must be complete. Before starting, run `./gradlew publishToMavenLocal` in `C:\Users\ncobu\landclaiming`. Then work in `C:\Users\ncobu\.codex\worktrees\9fd2\Haven\HavenVault-review` on branch `feature/claim-chunk-upgrades` branched from `main`.

---

## File Map

**Create (`havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/claim/`):**
- `ClaimUpgradeConfig.java` — YAML config record, BulkOption nested record
- `PurchaseResult.java` — purchase outcome record
- `ClaimChunkLimitEffect.java` — `UpgradeEffect` impl calling `HavenClaimsLimitService.addChunks`
- `ClaimChunkUpgradeService.java` — static pricing formula, purchase flow, availability guard

**Create (`havenvault-plugin/src/test/java/dev/invisiblespiders/havenvault/plugin/claim/`):**
- `ClaimUpgradeConfigTest.java`
- `ClaimChunkLimitEffectTest.java`
- `ClaimChunkPricingTest.java`
- `ClaimChunkUpgradeServiceTest.java`

**Modify:**
- `havenvault-plugin/build.gradle.kts`
- `havenvault-plugin/src/main/resources/config.yml`
- `havenvault-plugin/src/main/resources/plugin.yml`
- `havenvault-plugin/src/main/java/.../dialog/UpgradesDialog.java`
- `havenvault-plugin/src/main/java/.../dialog/BankDialog.java`
- `havenvault-plugin/src/main/java/.../dialog/BankDepositDialog.java`
- `havenvault-plugin/src/main/java/.../dialog/BankWithdrawDialog.java`
- `havenvault-plugin/src/main/java/.../command/BankCommand.java`
- `havenvault-plugin/src/main/java/.../command/UpgradesCommand.java`
- `havenvault-plugin/src/main/java/.../command/VaultCommand.java`
- `havenvault-plugin/src/main/java/.../HavenVaultPlugin.java`

---

## Task B1: Build dependency + ClaimUpgradeConfig

**Files:**
- Modify: `havenvault-plugin/build.gradle.kts`
- Modify: `havenvault-plugin/src/main/resources/config.yml`
- Create: `havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/claim/ClaimUpgradeConfig.java`
- Create: `havenvault-plugin/src/test/java/dev/invisiblespiders/havenvault/plugin/claim/ClaimUpgradeConfigTest.java`

- [ ] **Step 1: Write the failing test**

```java
// havenvault-plugin/src/test/java/dev/invisiblespiders/havenvault/plugin/claim/ClaimUpgradeConfigTest.java
package dev.invisiblespiders.havenvault.plugin.claim;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ClaimUpgradeConfigTest {

    private static YamlConfiguration yaml(String content) throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(content);
        return config;
    }

    @Test
    void parsesFullSection() throws Exception {
        YamlConfiguration config = yaml("""
            claim-upgrades:
              enabled: true
              base-cost: 250.0
              cost-multiplier: 1.2
              default-limit: 8
              currency: xp
              item-currency:
                material: EMERALD
                amount: 3
              bulk-options:
                - chunks: 1
                - chunks: 5
                  discount: 0.10
            """);

        ClaimUpgradeConfig cfg = ClaimUpgradeConfig.from(config);

        assertTrue(cfg.enabled());
        assertEquals(250.0, cfg.baseCost(), 0.001);
        assertEquals(1.2, cfg.costMultiplier(), 0.001);
        assertEquals(8, cfg.defaultLimit());
        assertEquals("xp", cfg.currency());
        assertEquals(Material.EMERALD, cfg.itemMaterial());
        assertEquals(3, cfg.itemAmount());
        assertEquals(2, cfg.bulkOptions().size());
        assertEquals(1, cfg.bulkOptions().get(0).chunks());
        assertEquals(0.0, cfg.bulkOptions().get(0).discount(), 0.001);
        assertEquals(5, cfg.bulkOptions().get(1).chunks());
        assertEquals(0.10, cfg.bulkOptions().get(1).discount(), 0.001);
    }

    @Test
    void returnsDisabledWhenSectionAbsent() throws Exception {
        assertFalse(ClaimUpgradeConfig.from(new YamlConfiguration()).enabled());
    }

    @Test
    void returnsDisabledWhenEnabledFalse() throws Exception {
        assertFalse(ClaimUpgradeConfig.from(yaml("claim-upgrades:\n  enabled: false")).enabled());
    }

    @Test
    void appliesDefaultsForMissingKeys() throws Exception {
        ClaimUpgradeConfig cfg = ClaimUpgradeConfig.from(yaml("claim-upgrades:\n  enabled: true"));
        assertEquals(500.0, cfg.baseCost(), 0.001);
        assertEquals(1.1, cfg.costMultiplier(), 0.001);
        assertEquals(10, cfg.defaultLimit());
        assertEquals("money", cfg.currency());
        assertEquals(Material.DIAMOND, cfg.itemMaterial());
        assertEquals(1, cfg.itemAmount());
        assertTrue(cfg.bulkOptions().isEmpty());
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```
cd C:\Users\ncobu\.codex\worktrees\9fd2\Haven\HavenVault-review
./gradlew :havenvault-plugin:test --tests "*.ClaimUpgradeConfigTest" -PuseMavenLocal=true
```

Expected: compilation error — `ClaimUpgradeConfig` not found.

- [ ] **Step 3: Add HavenClaims API dependency to `havenvault-plugin/build.gradle.kts`**

After the existing `compileOnly("dev.invisiblespiders.haven:haven-api:1.0.2")` line, add:

```kotlin
    compileOnly("com.nick:havenclaims-api:1.7.0-SNAPSHOT")
```

After the existing `testImplementation("dev.invisiblespiders.haven:haven-api:1.0.2")` line, add:

```kotlin
    testImplementation("com.nick:havenclaims-api:1.7.0-SNAPSHOT")
```

- [ ] **Step 4: Add `claim-upgrades:` section to `havenvault-plugin/src/main/resources/config.yml`**

Append to end of file (after the `date:` section):

```yaml

claim-upgrades:
  enabled: true
  default-limit: 10            # keep in sync with HavenClaims default-claim-limit
  base-cost: 500.0
  cost-multiplier: 1.1         # each chunk above default costs 10% more than the previous
  currency: money              # money | xp | item
  item-currency:
    material: DIAMOND
    amount: 1                  # items per chunk (flat, not formula-scaled)
  bulk-options:
    - chunks: 1
    - chunks: 5
      discount: 0.05
    - chunks: 10
      discount: 0.10
    - chunks: 25
      discount: 0.15
```

- [ ] **Step 5: Create `ClaimUpgradeConfig.java`**

```java
// havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/claim/ClaimUpgradeConfig.java
package dev.invisiblespiders.havenvault.plugin.claim;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

public record ClaimUpgradeConfig(
    boolean enabled,
    double baseCost,
    double costMultiplier,
    int defaultLimit,
    String currency,
    Material itemMaterial,
    int itemAmount,
    List<BulkOption> bulkOptions
) {
    public record BulkOption(int chunks, double discount) {}

    public static ClaimUpgradeConfig from(ConfigurationSection root) {
        ConfigurationSection sec = root.getConfigurationSection("claim-upgrades");
        if (sec == null) return disabled();
        if (!sec.getBoolean("enabled", false)) return disabled();

        double baseCost = sec.getDouble("base-cost", 500.0);
        double costMultiplier = sec.getDouble("cost-multiplier", 1.1);
        int defaultLimit = sec.getInt("default-limit", 10);
        String currency = sec.getString("currency", "money");

        Material itemMaterial = Material.DIAMOND;
        int itemAmount = 1;
        ConfigurationSection itemSec = sec.getConfigurationSection("item-currency");
        if (itemSec != null) {
            String mat = itemSec.getString("material", "DIAMOND");
            Material parsed = Material.matchMaterial(mat);
            if (parsed != null) itemMaterial = parsed;
            itemAmount = itemSec.getInt("amount", 1);
        }

        List<BulkOption> bulkOptions = new ArrayList<>();
        List<?> rawOptions = sec.getList("bulk-options");
        if (rawOptions != null) {
            for (Object raw : rawOptions) {
                if (!(raw instanceof Map<?, ?> map)) continue;
                int chunks = Integer.parseInt(String.valueOf(map.getOrDefault("chunks", "1")));
                double discount = Double.parseDouble(String.valueOf(map.getOrDefault("discount", "0")));
                bulkOptions.add(new BulkOption(chunks, discount));
            }
        }
        return new ClaimUpgradeConfig(true, baseCost, costMultiplier, defaultLimit,
            currency, itemMaterial, itemAmount, List.copyOf(bulkOptions));
    }

    private static ClaimUpgradeConfig disabled() {
        return new ClaimUpgradeConfig(false, 500.0, 1.1, 10, "money",
            Material.DIAMOND, 1, List.of());
    }
}
```

- [ ] **Step 6: Run the test to confirm it passes**

```
./gradlew :havenvault-plugin:test --tests "*.ClaimUpgradeConfigTest" -PuseMavenLocal=true
```

Expected: all 4 tests PASS.

- [ ] **Step 7: Commit**

```
git add havenvault-plugin/build.gradle.kts \
        havenvault-plugin/src/main/resources/config.yml \
        havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/claim/ClaimUpgradeConfig.java \
        havenvault-plugin/src/test/java/dev/invisiblespiders/havenvault/plugin/claim/ClaimUpgradeConfigTest.java
git commit -m "feat: add ClaimUpgradeConfig and claim-upgrades config section"
```

---

## Task B2: ClaimChunkLimitEffect + PurchaseResult

**Files:**
- Create: `havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/claim/PurchaseResult.java`
- Create: `havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/claim/ClaimChunkLimitEffect.java`
- Create: `havenvault-plugin/src/test/java/dev/invisiblespiders/havenvault/plugin/claim/ClaimChunkLimitEffectTest.java`

- [ ] **Step 1: Write the failing test**

```java
// havenvault-plugin/src/test/java/dev/invisiblespiders/havenvault/plugin/claim/ClaimChunkLimitEffectTest.java
package dev.invisiblespiders.havenvault.plugin.claim;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.invisiblespiders.havenclaims.api.service.HavenClaimsLimitService;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimChunkLimitEffectTest {

    @Test
    void delegatesAddChunksToLimitService() {
        HavenClaimsLimitService limitService = mock(HavenClaimsLimitService.class);
        UUID playerId = UUID.randomUUID();
        ClaimChunkLimitEffect effect = new ClaimChunkLimitEffect(limitService, 5);

        effect.apply(playerId, null, null);

        verify(limitService).addChunks(playerId, 5);
        verifyNoMoreInteractions(limitService);
    }

    @Test
    void rejectsZeroChunks() {
        HavenClaimsLimitService limitService = mock(HavenClaimsLimitService.class);
        assertThrows(IllegalArgumentException.class,
            () -> new ClaimChunkLimitEffect(limitService, 0));
    }

    @Test
    void rejectsNullLimitService() {
        assertThrows(NullPointerException.class,
            () -> new ClaimChunkLimitEffect(null, 1));
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```
./gradlew :havenvault-plugin:test --tests "*.ClaimChunkLimitEffectTest" -PuseMavenLocal=true
```

Expected: compilation error — `ClaimChunkLimitEffect` not found.

- [ ] **Step 3: Create `PurchaseResult.java`**

```java
// havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/claim/PurchaseResult.java
package dev.invisiblespiders.havenvault.plugin.claim;

public record PurchaseResult(boolean success, int newLimit, double cost, String message) {

    public static PurchaseResult success(int newLimit, double cost) {
        return new PurchaseResult(true, newLimit, cost, null);
    }

    public static PurchaseResult failure(String message) {
        return new PurchaseResult(false, 0, 0.0, message);
    }
}
```

- [ ] **Step 4: Create `ClaimChunkLimitEffect.java`**

```java
// havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/claim/ClaimChunkLimitEffect.java
package dev.invisiblespiders.havenvault.plugin.claim;

import com.invisiblespiders.havenclaims.api.service.HavenClaimsLimitService;
import dev.invisiblespiders.havenvault.api.service.HavenVaultBankService;
import dev.invisiblespiders.havenvault.api.service.HavenVaultStorageService;
import dev.invisiblespiders.havenvault.plugin.bank.upgrade.UpgradeEffect;
import java.util.Objects;
import java.util.UUID;

public final class ClaimChunkLimitEffect implements UpgradeEffect {

    private final HavenClaimsLimitService limitService;
    private final int chunks;

    public ClaimChunkLimitEffect(HavenClaimsLimitService limitService, int chunks) {
        this.limitService = Objects.requireNonNull(limitService, "limitService");
        if (chunks < 1) throw new IllegalArgumentException("chunks must be >= 1");
        this.chunks = chunks;
    }

    @Override
    public void apply(UUID playerId, HavenVaultBankService bankService,
                      HavenVaultStorageService storageService) {
        limitService.addChunks(playerId, chunks);
    }
}
```

- [ ] **Step 5: Run the test to confirm it passes**

```
./gradlew :havenvault-plugin:test --tests "*.ClaimChunkLimitEffectTest" -PuseMavenLocal=true
```

Expected: all 3 tests PASS.

- [ ] **Step 6: Commit**

```
git add havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/claim/PurchaseResult.java \
        havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/claim/ClaimChunkLimitEffect.java \
        havenvault-plugin/src/test/java/dev/invisiblespiders/havenvault/plugin/claim/ClaimChunkLimitEffectTest.java
git commit -m "feat: add ClaimChunkLimitEffect and PurchaseResult"
```

---

## Task B3: ClaimChunkUpgradeService

**Files:**
- Create: `havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/claim/ClaimChunkUpgradeService.java`
- Create: `havenvault-plugin/src/test/java/dev/invisiblespiders/havenvault/plugin/claim/ClaimChunkPricingTest.java`
- Create: `havenvault-plugin/src/test/java/dev/invisiblespiders/havenvault/plugin/claim/ClaimChunkUpgradeServiceTest.java`

- [ ] **Step 1: Write the pricing test**

```java
// havenvault-plugin/src/test/java/dev/invisiblespiders/havenvault/plugin/claim/ClaimChunkPricingTest.java
package dev.invisiblespiders.havenvault.plugin.claim;

import static dev.invisiblespiders.havenvault.plugin.claim.ClaimChunkUpgradeService.computeCost;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ClaimChunkPricingTest {

    @Test
    void singleChunkAtDefaultLimit() {
        // L=10, D=10 → exponent=0 → 500 × 1.0 = 500
        assertEquals(500.0, computeCost(500.0, 1.1, 10, 10, 1, 0.0), 0.001);
    }

    @Test
    void singleChunkOneAboveDefault() {
        // L=11, D=10 → exponent=1 → 500 × 1.1 = 550
        assertEquals(550.0, computeCost(500.0, 1.1, 11, 10, 1, 0.0), 0.001);
    }

    @Test
    void fiveChunksBulkWithFivePercentDiscount() {
        // L=10, D=10: chunk0=500, chunk1=550, chunk2=605, chunk3=665.5, chunk4=732.05
        // sum=3052.55 × 0.95 = 2899.9225
        assertEquals(2899.92, computeCost(500.0, 1.1, 10, 10, 5, 0.05), 0.1);
    }

    @Test
    void discountAppliedToFullSum() {
        // 10% discount on single chunk at default
        assertEquals(450.0, computeCost(500.0, 1.1, 10, 10, 1, 0.10), 0.001);
    }

    @Test
    void zeroChunksReturnsZero() {
        assertEquals(0.0, computeCost(500.0, 1.1, 10, 10, 0, 0.0), 0.001);
    }
}
```

- [ ] **Step 2: Write the service test**

```java
// havenvault-plugin/src/test/java/dev/invisiblespiders/havenvault/plugin/claim/ClaimChunkUpgradeServiceTest.java
package dev.invisiblespiders.havenvault.plugin.claim;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.invisiblespiders.havenclaims.api.service.HavenClaimsLimitService;
import dev.invisiblespiders.haven.api.service.HavenEconomyService;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class ClaimChunkUpgradeServiceTest {

    private final HavenClaimsLimitService limitService = mock(HavenClaimsLimitService.class);
    private final HavenEconomyService economy = mock(HavenEconomyService.class);
    private final Player player = mock(Player.class);
    private final UUID playerId = UUID.randomUUID();

    private ClaimChunkUpgradeService service(boolean withHavenClaims) {
        ClaimUpgradeConfig config = new ClaimUpgradeConfig(
            true, 500.0, 1.1, 10, "money", Material.DIAMOND, 1,
            List.of(new ClaimUpgradeConfig.BulkOption(1, 0.0))
        );
        return new ClaimChunkUpgradeService(
            config, withHavenClaims ? limitService : null, Logger.getLogger("test"));
    }

    @Test
    void isAvailableFalseWhenHavenClaimsAbsent() {
        assertFalse(service(false).isAvailable());
    }

    @Test
    void isAvailableTrueWhenHavenClaimsPresent() {
        assertTrue(service(true).isAvailable());
    }

    @Test
    void purchaseFailsGracefullyWhenUnavailable() {
        PurchaseResult result = service(false).purchase(player, economy, 1, 0.0);
        assertFalse(result.success());
        assertNotNull(result.message());
        verifyNoInteractions(limitService, economy);
    }

    @Test
    void purchasesChunkAndDeductsMoneyOnSuccess() {
        when(player.getUniqueId()).thenReturn(playerId);
        when(limitService.getLimit(playerId)).thenReturn(10, 11);
        when(economy.isMoneyAvailable()).thenReturn(true);
        when(economy.has(eq(playerId), anyDouble())).thenReturn(true);

        PurchaseResult result = service(true).purchase(player, economy, 1, 0.0);

        assertTrue(result.success());
        assertEquals(11, result.newLimit());
        assertEquals(500.0, result.cost(), 0.001);
        verify(economy).withdraw(eq(playerId), eq(500.0));
        verify(limitService).addChunks(playerId, 1);
    }

    @Test
    void rejectsWhenInsufficientFunds() {
        when(player.getUniqueId()).thenReturn(playerId);
        when(limitService.getLimit(playerId)).thenReturn(10);
        when(economy.isMoneyAvailable()).thenReturn(true);
        when(economy.has(eq(playerId), anyDouble())).thenReturn(false);

        PurchaseResult result = service(true).purchase(player, economy, 1, 0.0);

        assertFalse(result.success());
        verify(economy, never()).withdraw(any(), anyDouble());
        verify(limitService, never()).addChunks(any(), anyInt());
    }

    @Test
    void refundsMoneyWhenAddChunksFails() {
        when(player.getUniqueId()).thenReturn(playerId);
        when(limitService.getLimit(playerId)).thenReturn(10);
        when(economy.isMoneyAvailable()).thenReturn(true);
        when(economy.has(eq(playerId), anyDouble())).thenReturn(true);
        doThrow(new RuntimeException("DB error")).when(limitService).addChunks(any(), anyInt());

        PurchaseResult result = service(true).purchase(player, economy, 1, 0.0);

        assertFalse(result.success());
        verify(economy).withdraw(eq(playerId), anyDouble());
        verify(economy).deposit(eq(playerId), anyDouble());
    }
}
```

- [ ] **Step 3: Run tests to confirm they fail**

```
./gradlew :havenvault-plugin:test --tests "*.ClaimChunkPricingTest" --tests "*.ClaimChunkUpgradeServiceTest" -PuseMavenLocal=true
```

Expected: compilation error — `ClaimChunkUpgradeService` not found.

- [ ] **Step 4: Create `ClaimChunkUpgradeService.java`**

Pricing formula: `cost(L, n) = Σ_{i=0}^{n-1} [baseCost × multiplier^(L-D+i)] × (1 - discount)`

- money currency: formula output in dollars, paid via economy
- xp currency: formula output rounds up to XP levels
- item currency: `chunks × itemAmount` items consumed (flat, not formula-scaled)

```java
// havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/claim/ClaimChunkUpgradeService.java
package dev.invisiblespiders.havenvault.plugin.claim;

import com.invisiblespiders.havenclaims.api.service.HavenClaimsLimitService;
import dev.invisiblespiders.haven.api.service.HavenEconomyService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ClaimChunkUpgradeService {

    private final ClaimUpgradeConfig config;
    private final HavenClaimsLimitService limitService;
    private final Logger log;

    public ClaimChunkUpgradeService(ClaimUpgradeConfig config,
                                    HavenClaimsLimitService limitService,
                                    Logger log) {
        this.config = Objects.requireNonNull(config, "config");
        this.limitService = limitService; // nullable — null means HavenClaims absent
        this.log = Objects.requireNonNull(log, "log");
    }

    public boolean isAvailable() {
        return config.enabled() && limitService != null;
    }

    public ClaimUpgradeConfig config() {
        return config;
    }

    /**
     * Pure pricing formula. No side effects.
     * cost(L, n) = sum_{i=0}^{n-1} [ baseCost * multiplier^(L - D + i) ] * (1 - discount)
     */
    public static double computeCost(double baseCost, double costMultiplier,
                                     int currentLimit, int defaultLimit,
                                     int chunks, double discount) {
        double total = 0.0;
        for (int i = 0; i < chunks; i++) {
            total += baseCost * Math.pow(costMultiplier, currentLimit - defaultLimit + i);
        }
        return total * (1.0 - discount);
    }

    public double computeCostForPlayer(int currentLimit, int chunks, double discount) {
        return computeCost(config.baseCost(), config.costMultiplier(),
            currentLimit, config.defaultLimit(), chunks, discount);
    }

    public PurchaseResult purchase(Player player, HavenEconomyService economy,
                                   int chunks, double discount) {
        if (!isAvailable()) {
            return PurchaseResult.failure("Claim upgrades are not available.");
        }
        UUID playerId = player.getUniqueId();
        int currentLimit = limitService.getLimit(playerId);
        double cost = computeCostForPlayer(currentLimit, chunks, discount);

        if (!canAfford(player, economy, cost, chunks)) {
            return PurchaseResult.failure("You cannot afford this upgrade.");
        }

        deduct(player, economy, cost, chunks);

        try {
            new ClaimChunkLimitEffect(limitService, chunks).apply(playerId, null, null);
        } catch (Exception e) {
            refund(player, economy, cost, chunks);
            log.warning("addChunks failed for " + playerId + " after payment: " + e.getMessage());
            return PurchaseResult.failure("Upgrade failed. Contact an admin.");
        }

        int newLimit = limitService.getLimit(playerId);
        return PurchaseResult.success(newLimit, cost);
    }

    private boolean canAfford(Player player, HavenEconomyService economy,
                               double cost, int chunks) {
        return switch (config.currency()) {
            case "money" -> economy != null && economy.isMoneyAvailable()
                && economy.has(player.getUniqueId(), roundMoney(cost));
            case "xp" -> player.getLevel() >= (int) Math.ceil(cost);
            case "item" -> countItems(player, config.itemMaterial())
                >= config.itemAmount() * chunks;
            default -> false;
        };
    }

    private void deduct(Player player, HavenEconomyService economy,
                        double cost, int chunks) {
        switch (config.currency()) {
            case "money" -> economy.withdraw(player.getUniqueId(), roundMoney(cost));
            case "xp" -> player.setLevel(player.getLevel() - (int) Math.ceil(cost));
            case "item" -> consumeItems(player, config.itemMaterial(),
                config.itemAmount() * chunks);
        }
    }

    private void refund(Player player, HavenEconomyService economy,
                        double cost, int chunks) {
        try {
            switch (config.currency()) {
                case "money" -> economy.deposit(player.getUniqueId(), roundMoney(cost));
                case "xp" -> player.setLevel(player.getLevel() + (int) Math.ceil(cost));
                case "item" -> {
                    ItemStack refund = new ItemStack(config.itemMaterial(),
                        config.itemAmount() * chunks);
                    player.getInventory().addItem(refund).values()
                        .forEach(overflow ->
                            player.getWorld().dropItemNaturally(player.getLocation(), overflow));
                }
            }
        } catch (Exception e) {
            log.warning("Refund failed for " + player.getUniqueId() + ": " + e.getMessage());
        }
    }

    private static double roundMoney(double amount) {
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static int countItems(Player player, Material material) {
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == material) total += item.getAmount();
        }
        return total;
    }

    private static void consumeItems(Player player, Material material, int amount) {
        int remaining = amount;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() != material || remaining <= 0) continue;
            if (item.getAmount() <= remaining) {
                remaining -= item.getAmount();
                player.getInventory().remove(item);
            } else {
                item.setAmount(item.getAmount() - remaining);
                remaining = 0;
            }
        }
    }
}
```

- [ ] **Step 5: Run the tests to confirm they pass**

```
./gradlew :havenvault-plugin:test --tests "*.ClaimChunkPricingTest" --tests "*.ClaimChunkUpgradeServiceTest" -PuseMavenLocal=true
```

Expected: all 9 tests PASS.

- [ ] **Step 6: Commit**

```
git add havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/claim/ClaimChunkUpgradeService.java \
        havenvault-plugin/src/test/java/dev/invisiblespiders/havenvault/plugin/claim/ClaimChunkPricingTest.java \
        havenvault-plugin/src/test/java/dev/invisiblespiders/havenvault/plugin/claim/ClaimChunkUpgradeServiceTest.java
git commit -m "feat: add ClaimChunkUpgradeService with pricing formula and purchase flow"
```

---

## Task B4: Dialog plumbing — thread `ClaimChunkUpgradeService` through all dialog/command methods

All dialog `show()` methods form a navigation chain. `ClaimChunkUpgradeService` must be passed through every link so `UpgradesDialog` can use it. The changes are mechanical: add `@Nullable ClaimChunkUpgradeService claimUpgradeService` as the last parameter and pass it to every internal call.

**Files:**
- Modify: `havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/dialog/BankDialog.java`
- Modify: `havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/dialog/BankDepositDialog.java`
- Modify: `havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/dialog/BankWithdrawDialog.java`
- Modify: `havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/dialog/UpgradesDialog.java`
- Modify: `havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/command/BankCommand.java`
- Modify: `havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/command/UpgradesCommand.java`

- [ ] **Step 1: Update `BankDialog.java`**

Change `show()` signature:
```java
// OLD
public static void show(
    Player player,
    HavenVaultBankService bankService,
    HavenVaultStorageService storageService,
    HavenEconomyService economy,
    UpgradeRegistry upgradeRegistry,
    GuiSettings.BankDialogSettings settings
)

// NEW — add import: import dev.invisiblespiders.havenvault.plugin.claim.ClaimChunkUpgradeService;
public static void show(
    Player player,
    HavenVaultBankService bankService,
    HavenVaultStorageService storageService,
    HavenEconomyService economy,
    UpgradeRegistry upgradeRegistry,
    GuiSettings.BankDialogSettings settings,
    ClaimChunkUpgradeService claimUpgradeService
)
```

Update the three internal calls inside `show()`:

```java
// Deposit button click — was:
BankDepositDialog.show(p, bankService, storageService, economy, upgradeRegistry, settings);
// now:
BankDepositDialog.show(p, bankService, storageService, economy, upgradeRegistry, settings, claimUpgradeService);

// Withdraw button click — was:
BankWithdrawDialog.show(p, bankService, storageService, economy, upgradeRegistry, settings);
// now:
BankWithdrawDialog.show(p, bankService, storageService, economy, upgradeRegistry, settings, claimUpgradeService);

// Upgrades button click — was:
UpgradesDialog.show(p, bankService, storageService, economy, upgradeRegistry, settings);
// now:
UpgradesDialog.show(p, bankService, storageService, economy, upgradeRegistry, settings, claimUpgradeService);
```

- [ ] **Step 2: Update `BankDepositDialog.java`**

Add `import dev.invisiblespiders.havenvault.plugin.claim.ClaimChunkUpgradeService;`

Change `show()` signature — add `ClaimChunkUpgradeService claimUpgradeService` as last param.

Change `presetBtn()` signature — add `ClaimChunkUpgradeService claimUpgradeService` as last param (before the existing `ClickCallback.Options opts` param):
```java
// OLD
private static ActionButton presetBtn(
    String label, BigDecimal amount,
    HavenVaultBankService bankService, HavenVaultStorageService storageService,
    HavenEconomyService economy, UpgradeRegistry upgradeRegistry,
    GuiSettings.BankDialogSettings settings,
    ClickCallback.Options opts
)

// NEW
private static ActionButton presetBtn(
    String label, BigDecimal amount,
    HavenVaultBankService bankService, HavenVaultStorageService storageService,
    HavenEconomyService economy, UpgradeRegistry upgradeRegistry,
    GuiSettings.BankDialogSettings settings,
    ClaimChunkUpgradeService claimUpgradeService,
    ClickCallback.Options opts
)
```

Change `doDeposit()` signature — add `ClaimChunkUpgradeService claimUpgradeService` as second-to-last param (before `GuiSettings.BankDialogSettings settings`):
```java
// OLD
static void doDeposit(
    Player player, BigDecimal amount,
    HavenVaultBankService bankService, HavenVaultStorageService storageService,
    HavenEconomyService economy, UpgradeRegistry upgradeRegistry,
    GuiSettings.BankDialogSettings settings
)

// NEW
static void doDeposit(
    Player player, BigDecimal amount,
    HavenVaultBankService bankService, HavenVaultStorageService storageService,
    HavenEconomyService economy, UpgradeRegistry upgradeRegistry,
    GuiSettings.BankDialogSettings settings,
    ClaimChunkUpgradeService claimUpgradeService
)
```

Update all call sites inside `BankDepositDialog.java`:

In `show()`, the 4 `presetBtn()` calls — add `claimUpgradeService` before `opts`:
```java
// was: presetBtn("10%", pct(wallet, 10), bankService, storageService, economy, upgradeRegistry, settings, opts)
// now:
presetBtn("10%",  pct(wallet, 10),  bankService, storageService, economy, upgradeRegistry, settings, claimUpgradeService, opts)
presetBtn("25%",  pct(wallet, 25),  bankService, storageService, economy, upgradeRegistry, settings, claimUpgradeService, opts)
presetBtn("50%",  pct(wallet, 50),  bankService, storageService, economy, upgradeRegistry, settings, claimUpgradeService, opts)
presetBtn("All",  wallet,           bankService, storageService, economy, upgradeRegistry, settings, claimUpgradeService, opts)
```

In `show()`, confirmBtn lambda self-recursion:
```java
// was: BankDepositDialog.show(p, bankService, storageService, economy, upgradeRegistry, settings);
BankDepositDialog.show(p, bankService, storageService, economy, upgradeRegistry, settings, claimUpgradeService);
```

In `show()`, confirmBtn lambda `doDeposit` call:
```java
// was: doDeposit(p, amount, bankService, storageService, economy, upgradeRegistry, settings);
doDeposit(p, amount, bankService, storageService, economy, upgradeRegistry, settings, claimUpgradeService);
```

In `show()`, backBtn lambda:
```java
// was: BankDialog.show(p, bankService, storageService, economy, upgradeRegistry, settings);
BankDialog.show(p, bankService, storageService, economy, upgradeRegistry, settings, claimUpgradeService);
```

In `presetBtn()` lambda, `doDeposit` call:
```java
// was: doDeposit(p, amount, bankService, storageService, economy, upgradeRegistry, settings);
doDeposit(p, amount, bankService, storageService, economy, upgradeRegistry, settings, claimUpgradeService);
```

In `presetBtn()` lambda, `BankDialog.show` call (when amount <= 0):
```java
// was: BankDialog.show(p, bankService, storageService, economy, upgradeRegistry, settings);
BankDialog.show(p, bankService, storageService, economy, upgradeRegistry, settings, claimUpgradeService);
```

In `doDeposit()` body:
```java
// was: BankDialog.show(player, bankService, storageService, economy, upgradeRegistry, settings);
BankDialog.show(player, bankService, storageService, economy, upgradeRegistry, settings, claimUpgradeService);
```

- [ ] **Step 3: Update `BankWithdrawDialog.java`**

Apply the exact same pattern as `BankDepositDialog.java`:
- Add `ClaimChunkUpgradeService claimUpgradeService` to `show()`, `presetBtn()`, `doWithdraw()` signatures
- Thread it through all `BankDialog.show()`, `BankWithdrawDialog.show()`, `doWithdraw()` call sites inside the file
- Add the import

- [ ] **Step 4: Update `UpgradesDialog.java`**

Add `import dev.invisiblespiders.havenvault.plugin.claim.ClaimChunkUpgradeService;`

Change `show()` signature:
```java
// OLD
public static void show(
    Player player,
    HavenVaultBankService bankService,
    HavenVaultStorageService storageService,
    HavenEconomyService economy,
    UpgradeRegistry upgradeRegistry,
    GuiSettings.BankDialogSettings settings
)

// NEW
public static void show(
    Player player,
    HavenVaultBankService bankService,
    HavenVaultStorageService storageService,
    HavenEconomyService economy,
    UpgradeRegistry upgradeRegistry,
    GuiSettings.BankDialogSettings settings,
    ClaimChunkUpgradeService claimUpgradeService
)
```

Update self-refresh call inside the purchase button lambda:
```java
// was: UpgradesDialog.show(p, bankService, storageService, economy, upgradeRegistry, settings);
UpgradesDialog.show(p, bankService, storageService, economy, upgradeRegistry, settings, claimUpgradeService);
```

Update the back-to-bank button lambda:
```java
// was: BankDialog.show(p, bankService, storageService, economy, upgradeRegistry, settings);
BankDialog.show(p, bankService, storageService, economy, upgradeRegistry, settings, claimUpgradeService);
```

(The actual claim section rendering will be added in Task B5.)

- [ ] **Step 5: Update `BankCommand.java`**

Add field and constructor param:
```java
// Add import:
import dev.invisiblespiders.havenvault.plugin.claim.ClaimChunkUpgradeService;

// Add field after existing fields:
private final ClaimChunkUpgradeService claimUpgradeService;

// OLD constructor:
public BankCommand(
    HavenVaultBankService bankService,
    HavenVaultStorageService storageService,
    HavenEconomyService economy,
    Supplier<UpgradeRegistry> upgradeRegistry,
    Supplier<GuiSettings.BankDialogSettings> dialogSettings
)

// NEW constructor — add last param:
public BankCommand(
    HavenVaultBankService bankService,
    HavenVaultStorageService storageService,
    HavenEconomyService economy,
    Supplier<UpgradeRegistry> upgradeRegistry,
    Supplier<GuiSettings.BankDialogSettings> dialogSettings,
    ClaimChunkUpgradeService claimUpgradeService
)
```

In the constructor body add: `this.claimUpgradeService = claimUpgradeService;`

Update the `BankDialog.show()` call in `onCommand()`:
```java
// was:
BankDialog.show(player, bankService, storageService, economy, upgradeRegistry.get(), dialogSettings.get());
// now:
BankDialog.show(player, bankService, storageService, economy, upgradeRegistry.get(), dialogSettings.get(), claimUpgradeService);
```

- [ ] **Step 6: Update `UpgradesCommand.java`**

Apply same pattern as `BankCommand.java`:
- Add import and field `ClaimChunkUpgradeService claimUpgradeService`
- Add as last constructor param with assignment
- Update `UpgradesDialog.show()` call to pass `claimUpgradeService`

- [ ] **Step 7: Confirm the build compiles**

```
./gradlew :havenvault-plugin:compileJava -PuseMavenLocal=true
```

Expected: BUILD SUCCESSFUL (no compiler errors).

- [ ] **Step 8: Commit**

```
git add havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/dialog/ \
        havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/command/BankCommand.java \
        havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/command/UpgradesCommand.java
git commit -m "refactor: thread ClaimChunkUpgradeService through dialog/command chain"
```

---

## Task B5: UpgradesDialog claim section + HavenVaultPlugin wiring + admin commands + plugin.yml

**Files:**
- Modify: `havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/dialog/UpgradesDialog.java`
- Modify: `havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/HavenVaultPlugin.java`
- Modify: `havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/command/VaultCommand.java`
- Modify: `havenvault-plugin/src/main/resources/plugin.yml`

- [ ] **Step 1: Add claim section to `UpgradesDialog.java`**

At the top of `show()`, after the existing `List<ActionButton> buttons = new ArrayList<>()` loop, add the claim section. The claim buttons come after the regular upgrade buttons.

First add these imports to `UpgradesDialog.java`:
```java
import com.invisiblespiders.havenclaims.api.service.HavenClaimsLimitService;
import dev.invisiblespiders.havenvault.plugin.claim.ClaimChunkUpgradeService;
import dev.invisiblespiders.havenvault.plugin.claim.ClaimUpgradeConfig;
import dev.invisiblespiders.havenvault.plugin.claim.PurchaseResult;
```

After the existing `for (UpgradeDefinition def : upgrades)` loop (which fills `buttons`), add:

```java
if (claimUpgradeService != null && claimUpgradeService.isAvailable()) {
    ClaimUpgradeConfig claimCfg = claimUpgradeService.config();
    int currentLimit = claimUpgradeService.isAvailable()
        ? ((com.invisiblespiders.havenclaims.api.service.HavenClaimsLimitService)
           org.bukkit.Bukkit.getServicesManager()
               .load(com.invisiblespiders.havenclaims.api.service.HavenClaimsLimitService.class))
           .getLimit(player.getUniqueId())
        : claimCfg.defaultLimit();
```

Wait, that's awkward. `ClaimChunkUpgradeService` already encapsulates the limit lookup. Let me add a method to `ClaimChunkUpgradeService`:

```java
public int getCurrentLimit(UUID playerId) {
    return limitService != null ? limitService.getLimit(playerId) : config.defaultLimit();
}
```

Then in `UpgradesDialog`:
```java
if (claimUpgradeService != null && claimUpgradeService.isAvailable()) {
    ClaimUpgradeConfig claimCfg = claimUpgradeService.config();
    int currentLimit = claimUpgradeService.getCurrentLimit(player.getUniqueId());

    for (ClaimUpgradeConfig.BulkOption opt : claimCfg.bulkOptions()) {
        double cost = claimUpgradeService.computeCostForPlayer(currentLimit, opt.chunks(), opt.discount());
        String costLabel = formatClaimCost(claimCfg, economy, cost, opt.chunks());
        String pluralChunks = opt.chunks() == 1 ? "Chunk" : "Chunks";
        Component label = Component.text(
            "+" + opt.chunks() + " " + pluralChunks + " — " + costLabel
        ).color(NamedTextColor.AQUA);

        Component tooltip = buildClaimTooltip(claimCfg, economy, currentLimit, opt.chunks(), opt.discount());

        buttons.add(ActionButton.builder(label)
            .tooltip(tooltip)
            .action(DialogAction.customClick((view, audience) -> {
                if (!(audience instanceof Player p)) return;
                PurchaseResult result = claimUpgradeService.purchase(
                    p, economy, opt.chunks(), opt.discount());
                if (result.success()) {
                    p.sendMessage(Component.text(
                        "+" + opt.chunks() + " chunk(s) purchased. New limit: "
                        + result.newLimit() + ".", NamedTextColor.GREEN));
                } else {
                    p.sendMessage(Component.text(result.message(), NamedTextColor.RED));
                }
                UpgradesDialog.show(p, bankService, storageService, economy,
                    upgradeRegistry, settings, claimUpgradeService);
            }, BankDialog.oneTimeOpts()))
            .build());
    }
}
```

Also add the private helper methods to `UpgradesDialog`:

```java
private static String formatClaimCost(ClaimUpgradeConfig cfg, HavenEconomyService economy,
                                      double cost, int chunks) {
    return switch (cfg.currency()) {
        case "money" -> economy != null
            ? economy.format(cost)
            : String.format("$%.2f", cost);
        case "xp" -> (int) Math.ceil(cost) + " XP Levels";
        case "item" -> (cfg.itemAmount() * chunks) + "x "
            + cfg.itemMaterial().name().toLowerCase().replace('_', ' ');
        default -> String.format("%.2f", cost);
    };
}

private static Component buildClaimTooltip(ClaimUpgradeConfig cfg, HavenEconomyService economy,
                                           int currentLimit, int chunks, double discount) {
    StringBuilder sb = new StringBuilder();
    sb.append("Current limit: ").append(currentLimit)
      .append(" → New limit: ").append(currentLimit + chunks);
    if (discount > 0) {
        sb.append(String.format(" (%.0f%% discount)", discount * 100));
    }
    double total = ClaimChunkUpgradeService.computeCost(
        cfg.baseCost(), cfg.costMultiplier(), currentLimit, cfg.defaultLimit(), chunks, discount);
    if ("money".equals(cfg.currency())) {
        sb.append("\nTotal: ").append(economy != null
            ? economy.format(total) : String.format("$%.2f", total));
    }
    return Component.text(sb.toString());
}
```

Also add `getCurrentLimit` to `ClaimChunkUpgradeService.java`:
```java
public int getCurrentLimit(UUID playerId) {
    return limitService != null ? limitService.getLimit(playerId) : config.defaultLimit();
}
```

Also update the `body` list in `UpgradesDialog.show()` to add a claim section header when claim upgrades are available:

```java
List<DialogBody> body = new ArrayList<>();
body.add(DialogBody.plainMessage(Component.text(
    "  ✔ = owned   ⬆ = available   ✘ = requirements not met"
).color(NamedTextColor.GRAY)));
if (upgrades.isEmpty() && (claimUpgradeService == null || !claimUpgradeService.isAvailable())) {
    body.add(DialogBody.plainMessage(Component.text("No upgrades configured.")));
}
if (claimUpgradeService != null && claimUpgradeService.isAvailable()
        && claimUpgradeService.config().bulkOptions().isEmpty()) {
    body.add(DialogBody.plainMessage(Component.text(
        "No claim upgrade tiers configured.").color(NamedTextColor.GRAY)));
}
```

- [ ] **Step 2: Update `HavenVaultPlugin.java`**

Add imports at top of file:
```java
import com.invisiblespiders.havenclaims.api.service.HavenClaimsLimitService;
import dev.invisiblespiders.havenvault.plugin.claim.ClaimChunkUpgradeService;
import dev.invisiblespiders.havenvault.plugin.claim.ClaimUpgradeConfig;
```

In `onEnable()`, after `HavenEconomyService economyService = resolveEconomy();`, add:

```java
HavenClaimsLimitService havenClaimsLimitService = resolveHavenClaims();
ClaimUpgradeConfig claimUpgradeConfig = ClaimUpgradeConfig.from(getConfig());
ClaimChunkUpgradeService claimUpgradeService =
    new ClaimChunkUpgradeService(claimUpgradeConfig, havenClaimsLimitService, getLogger());
```

Add the resolver method at the bottom of the class (alongside `resolveEconomy()`):
```java
private HavenClaimsLimitService resolveHavenClaims() {
    HavenClaimsLimitService svc =
        getServer().getServicesManager().load(HavenClaimsLimitService.class);
    if (svc == null) {
        getLogger().info("HavenClaims not installed — claim chunk upgrades disabled.");
    }
    return svc;
}
```

Update the `BankCommand` constructor call (line ~174):
```java
// OLD
BankCommand bankCommand = new BankCommand(bankService, storageService, economyService,
    upgradeRegistryRef::get, () -> guiRef.get().bankDialog);

// NEW
BankCommand bankCommand = new BankCommand(bankService, storageService, economyService,
    upgradeRegistryRef::get, () -> guiRef.get().bankDialog, claimUpgradeService);
```

Update the `UpgradesCommand` constructor call (line ~180):
```java
// OLD
UpgradesCommand upgradesCommand = new UpgradesCommand(bankService, storageService, economyService,
    upgradeRegistryRef::get, () -> guiRef.get().bankDialog);

// NEW
UpgradesCommand upgradesCommand = new UpgradesCommand(bankService, storageService, economyService,
    upgradeRegistryRef::get, () -> guiRef.get().bankDialog, claimUpgradeService);
```

Update the `VaultCommand` constructor call (line ~163):
```java
// OLD
VaultCommand vaultCommand = new VaultCommand(
    this, backupManager, storageService, bankService, armoryService, upgradeRegistryRef::get, this::resolvePlayer);

// NEW
VaultCommand vaultCommand = new VaultCommand(
    this, backupManager, storageService, bankService, armoryService,
    upgradeRegistryRef::get, this::resolvePlayer, havenClaimsLimitService);
```

- [ ] **Step 3: Update `VaultCommand.java`**

Add imports:
```java
import com.invisiblespiders.havenclaims.api.service.HavenClaimsLimitService;
```

Add field after `playerResolver`:
```java
private final HavenClaimsLimitService havenClaimsLimitService; // nullable
```

Update constructor:
```java
// OLD
public VaultCommand(
    JavaPlugin plugin,
    BackupManager backupManager,
    HavenVaultStorageService storageService,
    HavenVaultBankService bankService,
    HavenVaultArmoryService armoryService,
    Supplier<UpgradeRegistry> upgradeRegistry,
    Function<String, Optional<UUID>> playerResolver
)

// NEW — add last param:
public VaultCommand(
    JavaPlugin plugin,
    BackupManager backupManager,
    HavenVaultStorageService storageService,
    HavenVaultBankService bankService,
    HavenVaultArmoryService armoryService,
    Supplier<UpgradeRegistry> upgradeRegistry,
    Function<String, Optional<UUID>> playerResolver,
    HavenClaimsLimitService havenClaimsLimitService
)
```

Add `this.havenClaimsLimitService = havenClaimsLimitService;` in constructor body.

In `handleAdmin()`, add `claim` case and update usage message:
```java
// OLD switch:
switch (args[1].toLowerCase()) {
    case "storage" -> handleAdminStorage(sender, args);
    case "bank"    -> handleAdminBank(sender, args);
    case "armory"  -> handleAdminArmory(sender, args);
    case "backup"  -> handleAdminBackup(sender, args);
    default        -> sender.sendMessage("Usage: /hvault admin <storage|bank|armory|backup> ...");
}

// NEW switch:
switch (args[1].toLowerCase()) {
    case "storage" -> handleAdminStorage(sender, args);
    case "bank"    -> handleAdminBank(sender, args);
    case "armory"  -> handleAdminArmory(sender, args);
    case "backup"  -> handleAdminBackup(sender, args);
    case "claim"   -> handleAdminClaim(sender, args);
    default        -> sender.sendMessage("Usage: /hvault admin <storage|bank|armory|backup|claim> ...");
}
```

Also fix the usage message in `onCommand()` when args.length < 2:
```java
// OLD: sender.sendMessage("Usage: /hvault admin <storage|bank|armory|backup> ...");
// NEW:
sender.sendMessage("Usage: /hvault admin <storage|bank|armory|backup|claim> ...");
```

Add the new handler methods at the end of the class (before the closing `}`):

```java
private void handleAdminClaim(CommandSender sender, String[] args) {
    if (!sender.hasPermission("havenvault.admin.claim")) {
        sender.sendMessage("You do not have permission to administer claim limits.");
        return;
    }
    if (havenClaimsLimitService == null) {
        sender.sendMessage("HavenClaims is not installed.");
        return;
    }
    if (args.length < 3 || !args[2].equalsIgnoreCase("limit")) {
        sender.sendMessage("Usage: /hvault admin claim limit <set|add|remove> <player|uuid> <amount>");
        return;
    }
    handleAdminClaimLimit(sender, args);
}

private void handleAdminClaimLimit(CommandSender sender, String[] args) {
    if (args.length < 6) {
        sender.sendMessage("Usage: /hvault admin claim limit <set|add|remove> <player|uuid> <amount>");
        return;
    }
    String subcommand = args[3].toLowerCase();
    String target = args[4];
    int amount;
    try {
        amount = Integer.parseInt(args[5]);
    } catch (NumberFormatException e) {
        sender.sendMessage("Amount must be a positive integer.");
        return;
    }
    if (amount < 1) {
        sender.sendMessage("Amount must be at least 1.");
        return;
    }
    Optional<UUID> playerId = resolveId(target);
    if (playerId.isEmpty()) {
        sender.sendMessage("Player not found: " + target);
        return;
    }
    switch (subcommand) {
        case "set" -> {
            havenClaimsLimitService.setLimit(playerId.get(), amount);
            sender.sendMessage("Set " + target + " claim chunk limit to " + amount + ".");
        }
        case "add" -> {
            havenClaimsLimitService.addChunks(playerId.get(), amount);
            int newLimit = havenClaimsLimitService.getLimit(playerId.get());
            sender.sendMessage("Added " + amount + " chunk(s) to " + target
                + ". New limit: " + newLimit + ".");
        }
        case "remove" -> {
            havenClaimsLimitService.removeChunks(playerId.get(), amount);
            int newLimit = havenClaimsLimitService.getLimit(playerId.get());
            sender.sendMessage("Removed " + amount + " chunk(s) from " + target
                + ". New limit: " + newLimit + ".");
        }
        default -> sender.sendMessage(
            "Usage: /hvault admin claim limit <set|add|remove> <player|uuid> <amount>");
    }
}

private Optional<UUID> resolveId(String arg) {
    try {
        return Optional.of(UUID.fromString(arg));
    } catch (IllegalArgumentException e) {
        return playerResolver.apply(arg);
    }
}
```

- [ ] **Step 4: Update `plugin.yml`**

Add `softdepend: [HavenClaims]` after the existing `depend:` block:
```yaml
softdepend:
  - HavenClaims
```

Add `havenvault.admin.claim` permission inside the `permissions:` block (after `havenvault.admin.backup`):
```yaml
  havenvault.admin.claim:
    description: Allows administration of HavenClaims chunk limits via HavenVault.
    default: op
```

Also add it as a child of `havenvault.admin`:
```yaml
  havenvault.admin:
    description: Grants administrative access to HavenVault systems.
    default: op
    children:
      havenvault.admin.storage: true
      havenvault.admin.bank: true
      havenvault.admin.armory: true
      havenvault.admin.backup: true
      havenvault.admin.claim: true   # add this line
      havenvault.armory.rename: true
```

- [ ] **Step 5: Full build + all tests pass**

```
./gradlew :havenvault-plugin:build -PuseMavenLocal=true
```

Expected: BUILD SUCCESSFUL — all tests pass, shadow jar verifies correctly.

- [ ] **Step 6: Commit**

```
git add havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/dialog/UpgradesDialog.java \
        havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/claim/ClaimChunkUpgradeService.java \
        havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/HavenVaultPlugin.java \
        havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/command/VaultCommand.java \
        havenvault-plugin/src/main/resources/plugin.yml
git commit -m "feat: add claim chunk upgrade dialog section and admin commands"
```

---

## Self-Review

### Spec coverage

| Spec section | Plan task |
|---|---|
| §6 HavenVault config | B1 (ClaimUpgradeConfig + config.yml) |
| §7 Pricing model | B3 (ClaimChunkUpgradeService.computeCost) |
| §8 ClaimChunkUpgradeService purchase flow | B3 |
| §8 ClaimChunkLimitEffect | B2 |
| §8 plugin.yml softdepend | B5 |
| §9 Dialog UI claim section | B5 |
| §9 Admin commands /vault admin claim limit | B5 |
| Availability check (null HavenClaims) | B3 isAvailable() + B5 HavenVaultPlugin |
| Currency: money/xp/item | B3 canAfford/deduct/refund |
| Bulk options with discount | B1 BulkOption + B3 computeCost |

### Placeholder scan

No "TBD", "TODO", or "implement later" found. All code blocks are complete.

### Type consistency

- `ClaimUpgradeConfig.BulkOption` used consistently across B1, B3, B5.
- `ClaimChunkUpgradeService.computeCost()` is static; `computeCostForPlayer()` delegates to it — both used in B3 tests and B5 dialog.
- `ClaimChunkUpgradeService.getCurrentLimit(UUID)` — added in B5 Step 1 and the method must be added to the class in B5 (the class is created in B3; the new method must be added there when Step 1 of B5 runs).
- `HavenClaimsLimitService` methods used: `getLimit(UUID)`, `setLimit(UUID, int)`, `addChunks(UUID, int)`, `removeChunks(UUID, int)` — all defined in the Phase A API interface.
