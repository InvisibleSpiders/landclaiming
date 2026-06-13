package com.nick.landclaims.plugin.limit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;
import javax.sql.DataSource;

public final class SqlClaimLimitRepository implements ClaimLimitRepository {
    private final DataSource dataSource;

    public SqlClaimLimitRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public OptionalInt getLimit(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        String sql = "SELECT chunk_limit FROM claim_player_limits WHERE player_uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return OptionalInt.of(rs.getInt("chunk_limit"));
                }
                return OptionalInt.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to get player limit.", e);
        }
    }

    @Override
    public void setLimit(UUID playerId, int limit) {
        Objects.requireNonNull(playerId, "playerId");
        String sql = "INSERT OR REPLACE INTO claim_player_limits (player_uuid, chunk_limit) VALUES (?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            stmt.setInt(2, limit);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to set player limit.", e);
        }
    }
}
