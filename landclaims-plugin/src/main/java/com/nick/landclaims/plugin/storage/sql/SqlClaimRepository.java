package com.nick.landclaims.plugin.storage.sql;

import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.ClaimChunk;
import com.nick.landclaims.plugin.claim.ClaimMember;
import com.nick.landclaims.plugin.claim.ClaimRole;
import com.nick.landclaims.plugin.claim.OwnerType;
import com.nick.landclaims.plugin.storage.ClaimRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;

public class SqlClaimRepository implements ClaimRepository {
    private final DataSource dataSource;

    public SqlClaimRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public void saveClaim(Claim claim) {
        Objects.requireNonNull(claim, "claim");
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                deleteClaim(connection, claim.id());
                insertClaim(connection, claim);
                insertChunks(connection, claim);
                insertFlags(connection, claim);
                insertMembers(connection, claim);
                insertDeniedPlayers(connection, claim);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save claim.", exception);
        }
    }

    @Override
    public void deleteClaim(UUID claimId) {
        Objects.requireNonNull(claimId, "claimId");
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                deleteClaim(connection, claimId);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete claim.", exception);
        }
    }

    @Override
    public Optional<Claim> findClaimAt(UUID worldId, int chunkX, int chunkZ) {
        Objects.requireNonNull(worldId, "worldId");
        String sql = """
                SELECT c.*
                FROM claims c
                INNER JOIN claim_chunks cc ON c.id = cc.claim_id
                WHERE cc.world_id = ? AND cc.chunk_x = ? AND cc.chunk_z = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, worldId.toString());
            statement.setInt(2, chunkX);
            statement.setInt(3, chunkZ);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapClaim(connection, resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to find claim by chunk.", exception);
        }
    }

    @Override
    public Optional<Claim> findClaimById(UUID claimId) {
        Objects.requireNonNull(claimId, "claimId");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM claims WHERE id = ?")) {
            statement.setString(1, claimId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapClaim(connection, resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to find claim by id.", exception);
        }
    }

    @Override
    public List<Claim> findClaimsByOwner(OwnerType ownerType, UUID ownerUuid) {
        Objects.requireNonNull(ownerType, "ownerType");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM claims WHERE owner_type = ? AND owner_uuid = ? ORDER BY created_at, id"
             )) {
            statement.setString(1, ownerType.name());
            statement.setString(2, ownerUuid.toString());
            return mapClaims(connection, statement);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to find claims by owner.", exception);
        }
    }

    @Override
    public List<Claim> findAllClaims() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM claims ORDER BY created_at, id")) {
            return mapClaims(connection, statement);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to list all claims.", exception);
        }
    }

    private void deleteClaim(Connection connection, UUID claimId) throws SQLException {
        executeDelete(connection, "DELETE FROM claim_members WHERE claim_id = ?", claimId);
        executeDelete(connection, "DELETE FROM claim_denied_players WHERE claim_id = ?", claimId);
        executeDelete(connection, "DELETE FROM claim_flags WHERE claim_id = ?", claimId);
        executeDelete(connection, "DELETE FROM claim_chunks WHERE claim_id = ?", claimId);
        executeDelete(connection, "DELETE FROM claims WHERE id = ?", claimId);
    }

    private void executeDelete(Connection connection, String sql, UUID claimId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, claimId.toString());
            statement.executeUpdate();
        }
    }

    private void insertClaim(Connection connection, Claim claim) throws SQLException {
        String sql = """
                INSERT INTO claims (id, name, owner_type, owner_uuid, world_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, claim.id().toString());
            statement.setString(2, claim.name());
            statement.setString(3, claim.owner().name());
            statement.setString(4, claim.ownerUuid() == null ? null : claim.ownerUuid().toString());
            statement.setString(5, claim.worldId().toString());
            statement.setString(6, claim.createdAt().toString());
            statement.setString(7, claim.updatedAt().toString());
            statement.executeUpdate();
        }
    }

    private void insertChunks(Connection connection, Claim claim) throws SQLException {
        String sql = "INSERT INTO claim_chunks (claim_id, world_id, chunk_x, chunk_z) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (ClaimChunk chunk : claim.claimChunks()) {
                statement.setString(1, claim.id().toString());
                statement.setString(2, chunk.worldId().toString());
                statement.setInt(3, chunk.chunkX());
                statement.setInt(4, chunk.chunkZ());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertFlags(Connection connection, Claim claim) throws SQLException {
        String sql = "INSERT INTO claim_flags (claim_id, flag_key, enabled) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Map.Entry<String, Boolean> flag : claim.flags().entrySet()) {
                statement.setString(1, claim.id().toString());
                statement.setString(2, flag.getKey());
                statement.setInt(3, Boolean.TRUE.equals(flag.getValue()) ? 1 : 0);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertMembers(Connection connection, Claim claim) throws SQLException {
        String sql = "INSERT INTO claim_members (claim_id, member_uuid, role) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (ClaimMember member : claim.members()) {
                statement.setString(1, claim.id().toString());
                statement.setString(2, member.memberUuid().toString());
                statement.setString(3, member.role().name());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertDeniedPlayers(Connection connection, Claim claim) throws SQLException {
        String sql = "INSERT INTO claim_denied_players (claim_id, player_uuid) VALUES (?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (UUID deniedPlayer : claim.deniedPlayers()) {
                statement.setString(1, claim.id().toString());
                statement.setString(2, deniedPlayer.toString());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private List<Claim> mapClaims(Connection connection, PreparedStatement statement) throws SQLException {
        List<Claim> claims = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                claims.add(mapClaim(connection, resultSet));
            }
        }
        return List.copyOf(claims);
    }

    private Claim mapClaim(Connection connection, ResultSet resultSet) throws SQLException {
        UUID claimId = UUID.fromString(resultSet.getString("id"));
        UUID worldId = UUID.fromString(resultSet.getString("world_id"));
        return new Claim(
                claimId,
                resultSet.getString("name"),
                OwnerType.valueOf(resultSet.getString("owner_type")),
                nullableUuid(resultSet.getString("owner_uuid")),
                worldId,
                loadChunks(connection, claimId),
                loadFlags(connection, claimId),
                loadMembers(connection, claimId),
                loadDeniedPlayers(connection, claimId),
                Instant.parse(resultSet.getString("created_at")),
                Instant.parse(resultSet.getString("updated_at"))
        );
    }

    private Set<ClaimChunk> loadChunks(Connection connection, UUID claimId) throws SQLException {
        Set<ClaimChunk> chunks = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT world_id, chunk_x, chunk_z FROM claim_chunks WHERE claim_id = ?"
        )) {
            statement.setString(1, claimId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    chunks.add(new ClaimChunk(
                            UUID.fromString(resultSet.getString("world_id")),
                            resultSet.getInt("chunk_x"),
                            resultSet.getInt("chunk_z")
                    ));
                }
            }
        }
        return Set.copyOf(chunks);
    }

    private Map<String, Boolean> loadFlags(Connection connection, UUID claimId) throws SQLException {
        Map<String, Boolean> flags = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT flag_key, enabled FROM claim_flags WHERE claim_id = ?"
        )) {
            statement.setString(1, claimId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    flags.put(resultSet.getString("flag_key"), resultSet.getInt("enabled") != 0);
                }
            }
        }
        return Map.copyOf(flags);
    }

    private Set<ClaimMember> loadMembers(Connection connection, UUID claimId) throws SQLException {
        Set<ClaimMember> members = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT member_uuid, role FROM claim_members WHERE claim_id = ?"
        )) {
            statement.setString(1, claimId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    members.add(new ClaimMember(
                            UUID.fromString(resultSet.getString("member_uuid")),
                            ClaimRole.valueOf(resultSet.getString("role"))
                    ));
                }
            }
        }
        return Set.copyOf(members);
    }

    private Set<UUID> loadDeniedPlayers(Connection connection, UUID claimId) throws SQLException {
        Set<UUID> deniedPlayers = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT player_uuid FROM claim_denied_players WHERE claim_id = ?"
        )) {
            statement.setString(1, claimId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    deniedPlayers.add(UUID.fromString(resultSet.getString("player_uuid")));
                }
            }
        }
        return Set.copyOf(deniedPlayers);
    }

    private UUID nullableUuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }
}
