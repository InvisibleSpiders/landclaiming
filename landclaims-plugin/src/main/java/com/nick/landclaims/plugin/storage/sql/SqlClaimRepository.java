package com.nick.landclaims.plugin.storage.sql;

import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.OwnerType;
import com.nick.landclaims.plugin.storage.ClaimRepository;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public class SqlClaimRepository implements ClaimRepository {
    private final DataSource dataSource;

    public SqlClaimRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public void initialize() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String createStatement : SqlStatements.CREATE_SCHEMA) {
                statement.execute(createStatement);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize claim repository schema.", exception);
        }
    }

    @Override
    public void saveClaim(Claim claim) {
        throw new UnsupportedOperationException("Saving claims is not implemented in the schema foundation task.");
    }

    @Override
    public Optional<Claim> findClaimAt(UUID worldId, int chunkX, int chunkZ) {
        throw new UnsupportedOperationException("Finding claims by chunk is not implemented in the schema foundation task.");
    }

    @Override
    public Optional<Claim> findClaimById(UUID claimId) {
        throw new UnsupportedOperationException("Finding claims by id is not implemented in the schema foundation task.");
    }

    @Override
    public List<Claim> findClaimsByOwner(OwnerType ownerType, UUID ownerUuid) {
        throw new UnsupportedOperationException("Finding claims by owner is not implemented in the schema foundation task.");
    }

    @Override
    public List<Claim> findAllClaims() {
        throw new UnsupportedOperationException("Listing all claims is not implemented in the schema foundation task.");
    }
}
