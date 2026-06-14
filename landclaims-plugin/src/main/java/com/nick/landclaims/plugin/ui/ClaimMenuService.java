package com.nick.landclaims.plugin.ui;

import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.OwnerType;
import com.nick.landclaims.plugin.message.MessageService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
                        new ClaimMenuAction(actionLabel("flags"), "/claim flags"),
                        new ClaimMenuAction(actionLabel("members"), "/claim member list"),
                        new ClaimMenuAction(actionLabel("info"), "/claim info")
                )
        );
    }

    private String actionLabel(String actionKey) {
        return messageService.renderPlain("claim.menu.action-labels." + actionKey, Map.of());
    }
}
