package com.invisiblespiders.havenclaims.plugin.upgrade;

import com.invisiblespiders.havenclaims.plugin.limit.LimitService;
import dev.invisiblespiders.haven.api.HavenAPI;
import dev.invisiblespiders.haven.api.service.HavenEconomyService;
import dev.invisiblespiders.haven.api.upgrade.UpgradeCategory;
import dev.invisiblespiders.haven.api.upgrade.UpgradeContext;
import dev.invisiblespiders.haven.api.upgrade.UpgradeDefinition;
import dev.invisiblespiders.haven.api.upgrade.UpgradeEffect;
import dev.invisiblespiders.haven.api.upgrade.UpgradeLevel;
import dev.invisiblespiders.haven.api.upgrade.UpgradeProvider;
import dev.invisiblespiders.haven.api.upgrade.UpgradeRequirement;
import dev.invisiblespiders.haven.api.upgrade.UpgradeRequirementResult;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class HavenClaimsUpgradeProvider implements UpgradeProvider {
    public static final String ID = HavenClaimsUpgradeConfig.PROVIDER_ID;
    private static final String DISPLAY_NAME = "HavenClaims";

    private final HavenClaimsUpgradeConfig config;
    private final LimitService limitService;
    private final HavenEconomyService economyService;
    private final List<UpgradeDefinition> definitions;

    public HavenClaimsUpgradeProvider(HavenClaimsUpgradeConfig config, LimitService limitService) {
        this(config, limitService, HavenAPI.get(HavenEconomyService.class));
    }

    HavenClaimsUpgradeProvider(
            HavenClaimsUpgradeConfig config,
            LimitService limitService,
            HavenEconomyService economyService
    ) {
        this.config = Objects.requireNonNull(config, "config");
        this.limitService = Objects.requireNonNull(limitService, "limitService");
        this.economyService = economyService;
        this.definitions = config.definitions().stream()
                .map(this::resolveDefinition)
                .toList();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return DISPLAY_NAME;
    }

    @Override
    public List<UpgradeCategory> categories() {
        return config.categories();
    }

    @Override
    public List<UpgradeDefinition> definitions() {
        return definitions;
    }

    @Override
    public Optional<UpgradeEffect> effect(String type, Map<String, String> values) {
        if (ClaimLimitEffect.TYPE.equals(type)) {
            // Support 'blocks' key primarily, fall back to 'chunks' (multiplied by 256 for backward-compat)
            int blocks = values.containsKey("blocks") 
                    ? positiveInt(values, "blocks") 
                    : positiveInt(values, "chunks") * 256;
            return Optional.of(new ClaimLimitEffect(limitService, blocks));
        }
        return Optional.empty();
    }

    @Override
    public Optional<UpgradeRequirement> requirement(String type, Map<String, String> values) {
        if ("money".equals(type)) {
            return Optional.of(new MoneyRequirement(economyService, positiveDouble(values, "amount")));
        }
        return Optional.empty();
    }

    private UpgradeDefinition resolveDefinition(UpgradeDefinition definition) {
        return new UpgradeDefinition(
                definition.id(),
                definition.providerId(),
                definition.category(),
                definition.scope(),
                definition.visibility(),
                definition.permission(),
                definition.levels().stream().map(this::resolveLevel).toList()
        );
    }

    private UpgradeLevel resolveLevel(UpgradeLevel level) {
        return new UpgradeLevel(
                level.level(),
                level.displayName(),
                level.requirements().stream().map(this::resolveRequirement).toList(),
                level.effects().stream().map(this::resolveEffect).toList(),
                level.metadata()
        );
    }

    private UpgradeRequirement resolveRequirement(UpgradeRequirement requirement) {
        if (requirement instanceof HavenClaimsUpgradeConfig.ConfiguredRequirement configured) {
            return requirement(configured.type(), configured.values())
                    .orElseThrow(() -> new IllegalArgumentException("Unsupported upgrade requirement: " + configured.type()));
        }
        return requirement;
    }

    private UpgradeEffect resolveEffect(UpgradeEffect effect) {
        if (effect instanceof HavenClaimsUpgradeConfig.ConfiguredEffect configured) {
            return effect(configured.type(), configured.values())
                    .orElseThrow(() -> new IllegalArgumentException("Unsupported upgrade effect: " + configured.type()));
        }
        return effect;
    }

    private static int positiveInt(Map<String, String> values, String key) {
        int value;
        try {
            value = Integer.parseInt(requiredValue(values, key));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be a positive integer", exception);
        }
        if (value < 1) {
            throw new IllegalArgumentException(key + " must be >= 1");
        }
        return value;
    }

    private static double positiveDouble(Map<String, String> values, String key) {
        double value;
        try {
            value = Double.parseDouble(requiredValue(values, key));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be a positive number", exception);
        }
        if (value <= 0.0D) {
            throw new IllegalArgumentException(key + " must be > 0");
        }
        return value;
    }

    private static String requiredValue(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing upgrade value: " + key);
        }
        return value;
    }

    private static final class MoneyRequirement implements UpgradeRequirement {
        private final HavenEconomyService economyService;
        private final double amount;

        private MoneyRequirement(HavenEconomyService economyService, double amount) {
            this.economyService = economyService;
            this.amount = amount;
        }

        @Override
        public String type() {
            return "money";
        }

        @Override
        public UpgradeRequirementResult validate(UpgradeContext context) {
            if (economyService == null || !economyService.isMoneyAvailable()) {
                return UpgradeRequirementResult.failure("economy-unavailable", "Economy is unavailable.");
            }
            if (!economyService.has(context.targetPlayerId(), amount)) {
                return UpgradeRequirementResult.failure("insufficient-funds", "You cannot afford this upgrade.");
            }
            return UpgradeRequirementResult.success();
        }

        @Override
        public void consume(UpgradeContext context) {
            if (economyService == null || !economyService.withdraw(context.targetPlayerId(), amount)) {
                throw new IllegalStateException("Unable to withdraw upgrade cost.");
            }
        }

        @Override
        public void refund(UpgradeContext context) {
            if (economyService != null) {
                economyService.deposit(context.targetPlayerId(), amount);
            }
        }
    }
}
