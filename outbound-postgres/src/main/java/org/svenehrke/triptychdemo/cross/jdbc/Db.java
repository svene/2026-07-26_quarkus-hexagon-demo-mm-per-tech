package org.svenehrke.triptychdemo.cross.jdbc;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Plain SQL over JDBC, without an ORM: every read maps its rows by hand, every write is an explicit statement.
 * <p>
 * Each call takes a connection from the pool and gives it back. Inside a {@code @Transactional} method Agroal hands
 * out the transaction's connection every time, so all statements of the method commit (or roll back) together; a
 * {@code SELECT ... FOR UPDATE} holds its locks until then.
 * <p>
 * Parameters: an enum is bound as its {@code name()}, an {@link Instant} as a {@code timestamp with time zone}.
 */
@ApplicationScoped
public class Db {

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    @Inject
    AgroalDataSource dataSource;

    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (var rs = statement.executeQuery()) {
                var rows = new ArrayList<T>();
                while (rs.next()) rows.add(mapper.map(rs));
                return rows;
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(sql, e);
        }
    }

    /** The first row, if there is one. */
    public <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        return query(sql, mapper, params).stream().findFirst();
    }

    /** A single number, e.g. a {@code coalesce(sum(...), 0)}. */
    public int queryInt(String sql, Object... params) {
        return queryOne(sql, rs -> rs.getInt(1), params).orElseThrow();
    }

    /** {@code INSERT}, {@code UPDATE} or {@code DELETE}; returns the number of rows changed. */
    public int update(String sql, Object... params) {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new UncheckedSqlException(sql, e);
        }
    }

    /** An {@code INSERT ... RETURNING id}; returns the generated id. */
    public long insert(String sql, Object... params) {
        return queryOne(sql, rs -> rs.getLong(1), params).orElseThrow();
    }

    public static Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static void bind(PreparedStatement statement, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, switch (params[i]) {
                case Enum<?> e -> e.name();
                case Instant instant -> OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
                case null, default -> params[i];
            });
        }
    }

    public static class UncheckedSqlException extends RuntimeException {
        UncheckedSqlException(String sql, SQLException cause) {
            super(cause.getMessage() + " [" + sql + "]", cause);
        }
    }
}
