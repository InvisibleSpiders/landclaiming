package com.invisiblespiders.havenclaims.plugin.storage.sql;

import com.invisiblespiders.havenclaims.api.flag.FlagState;
import com.invisiblespiders.havenclaims.plugin.claim.Claim;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimMember;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimRegion;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimRole;
import com.invisiblespiders.havenclaims.plugin.claim.OwnerType;
import com.invisiblespiders.havenclaims.plugin.storage.ClaimRepository;
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
                replaceClaim(connection, claim);
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
    public void replaceClaims(Claim replacementClaim, List<UUID> deletedClaimIds) {
        Objects.requireNonNull(replacementClaim, "replacementClaim");
        Objects.requireNonNull(deletedClaimIds, "deletedClaimIds");
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                for (UUID deletedClaimId : deletedClaimIds) {
                    deleteClaim(connection, deletedClaimId);
                }
                replaceClaim(connection, replacementClaim);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to replace claims.", exception);
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
        // Convert chunk coordinates to block coordinates
        int blockMinX = chunkX * 16;
        int blockMinZ = chunkZ * 16;
        int blockMaxX = blockMinX + 15;
        int blockMaxZ = blockMinZ + 15;

        String sql = """
                SELECT c.*
                FROM claims c
                INNER JOIN claim_block_regions cbr ON c.id = cbr.claim_id
                WHERE cbr.world_id = ? AND cbr.min_x <= ? AND cbr.max_x >= ? AND cbr.min_z <= ? AND cbr.max_z >= ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, worldId.toString());
            statement.setInt(2, blockMaxX);
            statement.setInt(3, blockMinX);
            statement.setInt(4, blockMaxZ);
            statement.setInt(5, blockMinZ);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                Claim claim = mapClaim(connection, resultSet);
                return Optional.ofNullable(claim);
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
                Claim claim = mapClaim(connection, resultSet);
                return Optional.ofNullable(claim);
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
        executeDelete(connection, "DELETE FROM claim_block_regions WHERE claim_id = ?", claimId);
        executeDelete(connection, "DELETE FROM claims WHERE id = ?", claimId);
    }

    private void replaceClaim(Connection connection, Claim claim) throws SQLException {
        deleteClaim(connection, claim.id());
        insertClaim(connection, claim);
        insertBlockRegion(connection, claim);
        insertFlags(connection, claim);
        insertMembers(connection, claim);
        insertDeniedPlayers(connection, claim);
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
            statement.setString(5, claim.region().worldId().toString());
            statement.setString(6, claim.createdAt().toString());
            statement.setString(7, claim.updatedAt().toString());
            statement.executeUpdate();
        }
    }

    private void insertBlockRegion(Connection connection, Claim claim) throws SQLException {
        String sql = "INSERT INTO claim_block_regions (claim_id, world_id, min_x, min_z, max_x, max_z) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, claim.id().toString());
            statement.setString(2, claim.region().worldId().toString());
            statement.setInt(3, claim.region().minX());
            statement.setInt(4, claim.region().minZ());
            statement.setInt(5, claim.region().maxX());
            statement.setInt(6, claim.region().maxZ());
            statement.executeUpdate();
        }
    }

    private void insertFlags(Connection connection, Claim claim) throws SQLException {
        String sql = "INSERT INTO claim_flags (claim_id, flag_key, enabled, state) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Map.Entry<String, FlagState> flag : claim.flags().entrySet()) {
                statement.setString(1, claim.id().toString());
                statement.setString(2, flag.getKey());
                statement.setInt(3, flag.getValue() == FlagState.OFF ? 0 : 1);
                statement.setString(4, flag.getValue().name());
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
        // Read every base row first, then bulk-load the child tables in one query each. This avoids
        // the 4-queries-per-claim N+1 pattern that mapClaim() incurs when used in a loop.
        List<ClaimRow> rows = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rows.add(new ClaimRow(
                        UUID.fromString(resultSet.getString("id")),
                        resultSet.getString("name"),
                        OwnerType.valueOf(resultSet.getString("owner_type")),
                        nullableUuid(resultSet.getString("owner_uuid")),
                        Instant.parse(resultSet.getString("created_at")),
                        Instant.parse(resultSet.getString("updated_at"))
                ));
            }
        }
        if (rows.isEmpty()) {
            return List.of();
        }

        List<UUID> claimIds = rows.stream().map(ClaimRow::id).toList();
        Map<UUID, ClaimRegion> regionsByClaim = bulkLoadRegions(connection, claimIds);
        Map<UUID, Map<String, FlagState>> flagsByClaim = bulkLoadFlags(connection, claimIds);
        Map<UUID, Set<ClaimMember>> membersByClaim = bulkLoadMembers(connection, claimIds);
        Map<UUID, Set<UUID>> deniedByClaim = bulkLoadDeniedPlayers(connection, claimIds);

        List<Claim> claims = new ArrayList<>(rows.size());
        for (ClaimRow row : rows) {
            ClaimRegion region = regionsByClaim.get(row.id());
            if (region == null) {
                continue; // data integrity: skip claims with no region
            }
            claims.add(new Claim(
                    row.id(),
                    row.name(),
                    row.ownerType(),
                    row.ownerUuid(),
                    region,
                    flagsByClaim.getOrDefault(row.id(), Map.of()),
                    membersByClaim.getOrDefault(row.id(), Set.of()),
                    deniedByClaim.getOrDefault(row.id(), Set.of()),
                    row.createdAt(),
                    row.updatedAt()
            ));
        }
        return List.copyOf(claims);
    }

    private record ClaimRow(
            UUID id,
            String name,
            OwnerType ownerType,
            UUID ownerUuid,
            Instant createdAt,
            Instant updatedAt
    ) {}

    private String inClausePlaceholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    private void bindClaimIds(PreparedStatement statement, List<UUID> claimIds) throws SQLException {
        for (int i = 0; i < claimIds.size(); i++) {
            statement.setString(i + 1, claimIds.get(i).toString());
        }
    }

    private Map<UUID, ClaimRegion> bulkLoadRegions(Connection connection, List<UUID> claimIds) throws SQLException {
        Map<UUID, ClaimRegion> result = new HashMap<>();
        String sql = "SELECT claim_id, world_id, min_x, min_z, max_x, max_z FROM claim_block_regions WHERE claim_id IN ("
                + inClausePlaceholders(claimIds.size()) + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindClaimIds(statement, claimIds);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    UUID claimId = UUID.fromString(resultSet.getString("claim_id"));
                    result.put(claimId, new ClaimRegion(
                            UUID.fromString(resultSet.getString("world_id")),
                            resultSet.getInt("min_x"),
                            resultSet.getInt("min_z"),
                            resultSet.getInt("max_x"),
                            resultSet.getInt("max_z")
                    ));
                }
            }
        }
        return result;
    }

    private Map<UUID, Map<String, FlagState>> bulkLoadFlags(Connection connection, List<UUID> claimIds) throws SQLException {
        Map<UUID, Map<String, FlagState>> result = new HashMap<>();
        String sql = "SELECT claim_id, flag_key, state FROM claim_flags WHERE claim_id IN ("
                + inClausePlaceholders(claimIds.size()) + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindClaimIds(statement, claimIds);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    UUID claimId = UUID.fromString(resultSet.getString("claim_id"));
                    result.computeIfAbsent(claimId, key -> new HashMap<>())
                            .put(resultSet.getString("flag_key"), parseState(resultSet.getString("state")));
                }
            }
        }
        return result;
    }

    private Map<UUID, Set<ClaimMember>> bulkLoadMembers(Connection connection, List<UUID> claimIds) throws SQLException {
        Map<UUID, Set<ClaimMember>> result = new HashMap<>();
        String sql = "SELECT claim_id, member_uuid, role FROM claim_members WHERE claim_id IN ("
                + inClausePlaceholders(claimIds.size()) + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindClaimIds(statement, claimIds);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    UUID claimId = UUID.fromString(resultSet.getString("claim_id"));
                    result.computeIfAbsent(claimId, key -> new HashSet<>()).add(new ClaimMember(
                            UUID.fromString(resultSet.getString("member_uuid")),
                            ClaimRole.valueOf(resultSet.getString("role"))
                    ));
                }
            }
        }
        return result;
    }

    private Map<UUID, Set<UUID>> bulkLoadDeniedPlayers(Connection connection, List<UUID> claimIds) throws SQLException {
        Map<UUID, Set<UUID>> result = new HashMap<>();
        String sql = "SELECT claim_id, player_uuid FROM claim_denied_players WHERE claim_id IN ("
                + inClausePlaceholders(claimIds.size()) + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindClaimIds(statement, claimIds);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    UUID claimId = UUID.fromString(resultSet.getString("claim_id"));
                    result.computeIfAbsent(claimId, key -> new HashSet<>())
                            .add(UUID.fromString(resultSet.getString("player_uuid")));
                }
            }
        }
        return result;
    }

    private Claim mapClaim(Connection connection, ResultSet resultSet) throws SQLException {
        UUID claimId = UUID.fromString(resultSet.getString("id"));
        List<UUID> ids = List.of(claimId);
        Map<UUID, ClaimRegion> regions = bulkLoadRegions(connection, ids);
        ClaimRegion region = regions.get(claimId);
        if (region == null) {
            return null; // data integrity: skip claims with no region
        }
        return new Claim(
                claimId,
                resultSet.getString("name"),
                OwnerType.valueOf(resultSet.getString("owner_type")),
                nullableUuid(resultSet.getString("owner_uuid")),
                region,
                loadFlags(connection, claimId),
                loadMembers(connection, claimId),
                loadDeniedPlayers(connection, claimId),
                Instant.parse(resultSet.getString("created_at")),
                Instant.parse(resultSet.getString("updated_at"))
        );
    }

    private Map<String, FlagState> loadFlags(Connection connection, UUID claimId) throws SQLException {
        Map<String, FlagState> flags = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT flag_key, state FROM claim_flags WHERE claim_id = ?"
        )) {
            statement.setString(1, claimId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    flags.put(resultSet.getString("flag_key"), parseState(resultSet.getString("state")));
                }
            }
        }
        return Map.copyOf(flags);
    }

    private FlagState parseState(String value) {
        if (value == null) {
            return FlagState.OFF;
        }
        try {
            return FlagState.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return FlagState.OFF;
        }
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
