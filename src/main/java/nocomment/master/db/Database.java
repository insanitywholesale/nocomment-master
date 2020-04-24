package nocomment.master.db;

import nocomment.master.NoComment;
import nocomment.master.clustering.DBSCAN;
import nocomment.master.util.Associator;
import nocomment.master.util.OnlinePlayer;
import org.apache.commons.dbcp2.BasicDataSource;

import java.sql.*;
import java.util.*;

public class Database {
    private static BasicDataSource pool;

    static {
        System.out.println("Connecting to database...");
        pool = new BasicDataSource();
        pool.setUsername("nocom");
        pool.setPassword("6bf40e917cdc202f627398c433899a0cc9aa8880a6dc25aacc4342779eccd227");
        pool.setDriverClassName("org.postgresql.Driver");
        pool.setUrl("jdbc:postgresql://localhost:5432/nocom");
        pool.setInitialSize(1);
        pool.setMaxTotal(75);
        pool.setAutoCommitOnReturn(true); // make absolutely sure
        pool.setDefaultTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        pool.setRollbackOnReturn(true);
        pool.setDefaultReadOnly(NoComment.DRY_RUN);
        System.out.println("Connected.");
        if (!NoComment.DRY_RUN) {
            Maintenance.scheduleMaintenance();
            DBSCAN.INSTANCE.beginIncrementalDBSCANThread();
            Associator.INSTANCE.beginIncrementalAssociatorThread();
        }
    }

    static void saveHit(Hit hit) {
        if (NoComment.DRY_RUN) {
            throw new IllegalStateException();
        }
        try (Connection connection = pool.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement stmt = connection.prepareStatement("INSERT INTO hits (created_at, x, z, dimension, server_id, track_id) VALUES (?, ?, ?, ?, ?, ?) RETURNING id")) {
                stmt.setLong(1, hit.createdAt);
                stmt.setInt(2, hit.pos.x);
                stmt.setInt(3, hit.pos.z);
                stmt.setShort(4, hit.dimension);
                stmt.setShort(5, hit.serverID);
                if (hit.getTrackID().isPresent()) {
                    stmt.setInt(6, hit.getTrackID().getAsInt());
                } else {
                    stmt.setNull(6, Types.INTEGER);
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    rs.next();
                    hit.setHitID(rs.getLong("id"));
                }
            }
            if (hit.getTrackID().isPresent()) {
                updateTrackWithMostRecentHit(hit, connection);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    public static void clearSessions(short serverID) {
        if (NoComment.DRY_RUN) {
            throw new IllegalStateException();
        }
        long mostRecent = mostRecentEvent(serverID);
        long setLeaveTo = mostRecent + 1; // range inclusivity
        if (setLeaveTo >= System.currentTimeMillis()) {
            // this will crash later, as soon as we try and add a player and the range overlaps
            // might as well crash early
            throw new RuntimeException("lol server clock went backwards");
        }
        try (Connection connection = pool.getConnection();
             PreparedStatement stmt = connection.prepareStatement("UPDATE player_sessions SET leave = ? WHERE range @> ? AND server_id = ?")) {
            stmt.setLong(1, setLeaveTo);
            stmt.setLong(2, Long.MAX_VALUE - 1); // must be -1 since postgres ranges are exclusive on the upper end
            stmt.setShort(3, serverID);
            stmt.executeUpdate();
        } catch (SQLException ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    private static long mostRecentEvent(short serverID) {
        try (Connection connection = pool.getConnection()) {
            long mostRecent;
            try (PreparedStatement stmt = connection.prepareStatement("SELECT MAX(created_at) FROM hits WHERE server_id = ?")) {
                stmt.setShort(1, serverID);
                try (ResultSet rs = stmt.executeQuery()) {
                    rs.next();
                    mostRecent = rs.getLong(1);
                }
            }
            try (PreparedStatement stmt = connection.prepareStatement("SELECT MAX(\"join\") FROM player_sessions WHERE range @> ? AND server_id = ?")) {
                stmt.setLong(1, Long.MAX_VALUE - 1); // must be -1 since postgres ranges are exclusive on the upper end
                stmt.setShort(2, serverID);
                try (ResultSet rs = stmt.executeQuery()) {
                    rs.next();
                    mostRecent = Math.max(mostRecent, rs.getLong(1));
                }
            }
            return mostRecent;
        } catch (SQLException ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    private static OptionalInt idForExistingPlayer(OnlinePlayer player) {
        try (
                Connection connection = pool.getConnection();
                PreparedStatement stmt = connection.prepareStatement(
                        player.hasUsername() ?
                                "UPDATE players SET username = ? WHERE uuid = ? RETURNING id"
                                : "SELECT id FROM players WHERE uuid = ?"
                )
        ) {
            if (player.hasUsername()) {
                stmt.setString(1, player.username);
                stmt.setObject(2, player.uuid);
            } else {
                stmt.setObject(1, player.uuid);
            }

            try (ResultSet existing = stmt.executeQuery()) {
                if (existing.next()) {
                    return OptionalInt.of(existing.getInt("id"));
                } else {
                    return OptionalInt.empty();
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    public static int idForPlayer(OnlinePlayer player) {
        OptionalInt existing = idForExistingPlayer(player);
        if (existing.isPresent()) {
            return existing.getAsInt();
        }
        try (Connection connection = pool.getConnection();
             PreparedStatement stmt = connection.prepareStatement("INSERT INTO players (username, uuid) VALUES (?, ?) RETURNING id")) {
            stmt.setString(1, player.username);
            stmt.setObject(2, player.uuid);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt("id");
            }
        } catch (SQLException ex) {
            ex.printStackTrace(); // two threads ask for same player for the first time, at same time
            return idForExistingPlayer(player).getAsInt();
        }
    }

    private static Optional<Short> idForExistingServer(String hostname) {
        try (Connection connection = pool.getConnection();
             PreparedStatement stmt = connection.prepareStatement("SELECT id FROM servers WHERE hostname = ?")) {
            stmt.setString(1, hostname);
            try (ResultSet existing = stmt.executeQuery()) {
                if (existing.next()) {
                    return Optional.of(existing.getShort("id"));
                } else {
                    return Optional.empty();
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    public static short idForServer(String hostname) {
        Optional<Short> existing = idForExistingServer(hostname);
        if (existing.isPresent()) {
            return existing.get();
        }
        try (Connection connection = pool.getConnection();
             PreparedStatement stmt = connection.prepareStatement("INSERT INTO servers (hostname) VALUES (?) RETURNING id")) {
            stmt.setString(1, hostname);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getShort("id");
            }
        } catch (SQLException ex) {
            ex.printStackTrace(); // two threads ask for same server for the first time, at same time
            return idForExistingServer(hostname).get();
        }
    }

    public static void addPlayers(short serverID, Collection<Integer> playerIDs, long now) {
        try (Connection connection = pool.getConnection();
             PreparedStatement stmt = connection.prepareStatement("INSERT INTO player_sessions (player_id, server_id, \"join\", leave) VALUES (?, ?, ?, NULL)")) {
            for (int playerID : playerIDs) {
                stmt.setInt(1, playerID);
                stmt.setShort(2, serverID);
                stmt.setLong(3, now);
                stmt.execute();
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    public static void removePlayers(short serverID, Collection<Integer> playerIDs, long now) {
        try (Connection connection = pool.getConnection();
             PreparedStatement stmt = connection.prepareStatement("UPDATE player_sessions SET leave = ? WHERE range @> ? AND player_id = ? AND server_id = ?")) {
            for (int playerID : playerIDs) {
                stmt.setLong(1, now);
                stmt.setLong(2, Long.MAX_VALUE - 1); // must be -1 since postgres ranges are exclusive on the upper end
                stmt.setInt(3, playerID);
                stmt.setShort(4, serverID);
                stmt.executeUpdate();
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    public static int createTrack(Hit initialHit, OptionalInt prevTrackID) {
        // make sure that the initialHit has an assigned hit ID
        initialHit.saveToDB();
        try (Connection connection = pool.getConnection();
             PreparedStatement stmt = connection.prepareStatement("INSERT INTO tracks (first_hit_id, last_hit_id, updated_at, prev_track_id, dimension, server_id) VALUES (?, ?, ?, ?, ?, ?) RETURNING id")) {
            stmt.setLong(1, initialHit.getHitID());
            stmt.setLong(2, initialHit.getHitID());
            stmt.setLong(3, initialHit.createdAt);
            if (prevTrackID.isPresent()) {
                stmt.setInt(4, prevTrackID.getAsInt());
            } else {
                stmt.setNull(4, Types.INTEGER);
            }
            stmt.setShort(5, initialHit.dimension);
            stmt.setShort(6, initialHit.serverID);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt("id");
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    static void addHitToTrack(Hit hit) {
        try (Connection connection = pool.getConnection()) {
            connection.setAutoCommit(false);
            updateTrackWithMostRecentHit(hit, connection);
            try (PreparedStatement stmt = connection.prepareStatement("UPDATE hits SET track_id = ? WHERE id = ?")) {
                stmt.setInt(1, hit.getTrackID().getAsInt());
                stmt.setLong(2, hit.getHitID());
                stmt.execute();
            }
            connection.commit();
        } catch (SQLException ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    private static void updateTrackWithMostRecentHit(Hit hit, Connection connection) throws SQLException {
        // preserve invariant of the last hit in the track having the highest updated_at
        try (PreparedStatement stmt = connection.prepareStatement("UPDATE tracks SET last_hit_id = ?, updated_at = ? WHERE id = ? AND updated_at <= ?")) {
            stmt.setLong(1, hit.getHitID());
            stmt.setLong(2, hit.createdAt);
            stmt.setInt(3, hit.getTrackID().getAsInt());
            stmt.setLong(4, hit.createdAt);
            stmt.executeUpdate();
        }
    }

    public static Set<Integer> trackIDsToResume(Collection<Integer> playerIDs, short serverID) {
        try (Connection connection = pool.getConnection()) {

            Set<Long> logoutTimestamps = new HashSet<>(); // set because there will be many duplicates

            try (PreparedStatement stmt = connection.prepareStatement("SELECT MAX(UPPER(range)) FROM player_sessions WHERE player_id = ? AND server_id = ?")) {
                for (int playerID : playerIDs) {
                    stmt.setInt(1, playerID);
                    stmt.setShort(2, serverID);
                    try (ResultSet rs = stmt.executeQuery()) {
                        rs.next();
                        long time = rs.getLong(1);
                        if (!rs.wasNull()) {
                            logoutTimestamps.add(time);
                        }
                    }
                }
            }

            Set<Integer> trackIDsToResume = new HashSet<>(); // set because there will be many duplicates

            if (logoutTimestamps.isEmpty()) {
                return trackIDsToResume;
            }

            try (PreparedStatement stmt = connection.prepareStatement("SELECT id FROM tracks WHERE updated_at >= ? AND updated_at <= ? AND server_id = ?")) {
                for (long logoutTimestamp : logoutTimestamps) {
                    stmt.setLong(1, logoutTimestamp - 60_000);
                    stmt.setLong(2, logoutTimestamp + 60_000); // plus or minus 1 minute
                    stmt.setShort(3, serverID);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            trackIDsToResume.add(rs.getInt(1));
                        }
                    }
                }
            }

            return trackIDsToResume;
        } catch (SQLException ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    public static List<TrackResume> resumeTracks(Collection<Integer> trackIDs) {
        try (Connection connection = pool.getConnection();
             PreparedStatement stmt = connection.prepareStatement("SELECT hits.x, hits.z, hits.dimension FROM tracks INNER JOIN hits ON hits.id = tracks.last_hit_id WHERE tracks.id = ?")) {
            List<TrackResume> ret = new ArrayList<>();
            for (int trackID : trackIDs) {
                stmt.setInt(1, trackID);
                try (ResultSet rs = stmt.executeQuery()) {
                    rs.next();
                    ret.add(new TrackResume(rs.getInt("x"), rs.getInt("z"), rs.getShort("dimension"), trackID));
                }
            }
            return ret;
        } catch (SQLException ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    public static OptionalLong currentSessionJoinedAt(int playerID, short serverID) {
        try (Connection connection = pool.getConnection();
             PreparedStatement stmt = connection.prepareStatement("SELECT \"join\" FROM player_sessions WHERE range @> ? AND player_id = ? AND server_id = ?")) {
            stmt.setLong(1, Long.MAX_VALUE - 1); // must be -1 since postgres ranges are exclusive on the upper end
            stmt.setInt(2, playerID);
            stmt.setShort(3, serverID);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return OptionalLong.of(rs.getLong("join"));
                } else {
                    return OptionalLong.empty();
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    public static Set<Integer> allPlayerIDsThatLeftBetween(long rangeStart, long rangeEnd, short serverID, Connection connection) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT player_id FROM player_sessions WHERE range && INT8RANGE(?, ?, '[]') AND server_id = ? AND leave IS NOT NULL AND leave >= ? AND leave <= ?")) {
            stmt.setLong(1, rangeStart);
            stmt.setLong(2, rangeEnd);
            stmt.setShort(3, serverID);
            stmt.setLong(4, rangeStart);
            stmt.setLong(5, rangeEnd);
            try (ResultSet rs = stmt.executeQuery()) {
                Set<Integer> ret = new HashSet<>();
                while (rs.next()) {
                    ret.add(rs.getInt("player_id"));
                }
                return ret;
            }
        }
    }

    static void vacuum() {
        try (Connection connection = pool.getConnection(); PreparedStatement stmt = connection.prepareStatement("VACUUM ANALYZE")) {
            stmt.execute();
        } catch (SQLException ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    static void reindex(String indexName) {
        try (Connection connection = pool.getConnection(); PreparedStatement stmt = connection.prepareStatement("REINDEX INDEX CONCURRENTLY " + indexName)) {
            stmt.execute();
        } catch (SQLException ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    public static Connection getConnection() throws SQLException {
        return pool.getConnection();
    }
}
