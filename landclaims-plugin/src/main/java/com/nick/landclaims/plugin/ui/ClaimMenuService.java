package com.nick.landclaims.plugin.ui;

import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.OwnerType;
import com.nick.landclaims.plugin.message.MessageService;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ClaimMenuService {
    private final MessageService messageService;

    public ClaimMenuService(MessageService messageService) {
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    public ClaimMenu buildMenu(Claim claim, UUID viewerId) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(viewerId, "viewerId");

        return new ClaimMenu(
                claim.name(),
                claim.owner().name(),
                claim.claimChunks().size(),
                claim.members().size(),
                claim.flags().size(),
                viewerId.equals(claim.ownerUuid()),
                claim.owner() == OwnerType.ADMIN,
                List.of(
                        new ClaimMenuAction(actionLabel("flags"), "/claim flags " + claim.id()),
                        new ClaimMenuAction(actionLabel("members"), "/claim member list " + claim.id()),
                        new ClaimMenuAction(actionLabel("denied"), "/claim denied " + claim.id()),
                        new ClaimMenuAction(actionLabel("info"), "/claim info " + claim.id()),
                        new ClaimMenuAction(actionLabel("viewborder"), "/claim viewborder " + claim.id()),
                        new ClaimMenuAction(actionLabel("delete"), "/claim delete " + claim.id())
                )
        );
    }

    public ClaimDashboard buildDashboard(List<Claim> claims, Optional<Claim> currentClaim) {
        Objects.requireNonNull(claims, "claims");
        Objects.requireNonNull(currentClaim, "currentClaim");

        List<ClaimDashboardRow> rows = claims.stream()
                .sorted(dashboardClaimOrder(currentClaim))
                .map(claim -> new ClaimDashboardRow(
                        claim.id(),
                        claim.name(),
                        claim.claimChunks().size(),
                        currentClaim.map(current -> current.id().equals(claim.id())).orElse(false),
                        "/claim menu " + claim.id()
                ))
                .toList();

        return new ClaimDashboard(
                messageService.renderPlainOrDefault("claim.dashboard.title", Map.of(), "My Claims"),
                rows,
                List.of(
                        new ClaimMenuAction(dashboardActionLabel("create"), "/claim create"),
                        new ClaimMenuAction(dashboardActionLabel("cost"), "/claim cost"),
                        new ClaimMenuAction(dashboardActionLabel("tool"), "/claim tool")
                )
        );
    }

    private String actionLabel(String actionKey) {
        return messageService.renderPlainOrDefault(
                "claim.menu.action-labels." + actionKey,
                Map.of(),
                defaultActionLabel(actionKey));
    }

    private String defaultActionLabel(String actionKey) {
        return switch (actionKey) {
            case "flags" -> "Flags";
            case "members" -> "Members";
            case "denied" -> "Denied Players";
            case "info" -> "Info";
            case "viewborder" -> "View Border";
            case "delete" -> "Delete Claim";
            default -> actionKey;
        };
    }

    private Comparator<Claim> dashboardClaimOrder(Optional<Claim> currentClaim) {
        return Comparator
                .comparing((Claim claim) -> currentClaim.map(current -> !current.id().equals(claim.id())).orElse(true))
                .thenComparing(Claim::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(claim -> claim.id().toString());
    }

    private String dashboardActionLabel(String actionKey) {
        return messageService.renderPlainOrDefault(
                "claim.dashboard.action-labels." + actionKey,
                Map.of(),
                defaultDashboardActionLabel(actionKey));
    }

    private String defaultDashboardActionLabel(String actionKey) {
        return switch (actionKey) {
            case "create" -> "Create Claim";
            case "cost" -> "Claim Cost";
            case "tool" -> "Claim Tool";
            default -> actionKey;
        };
    }
}
