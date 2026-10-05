package org.svenehrke.triptychdemo.cross.inventory;

import org.svenehrke.triptychdemo.cross.jdbc.Db;
import org.svenehrke.triptychdemo.cross.location.Location;
import org.svenehrke.triptychdemo.cross.products.ProductType;
import org.svenehrke.triptychdemo.cross.reorder.DemandEstimate;
import org.svenehrke.triptychdemo.cross.reorder.LearnedLevels;
import org.svenehrke.triptychdemo.cross.reorder.ReorderPolicy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * The SQL of the {@code stock} table (unique on locationId + name + type). A new row starts with the cold-start
 * estimate of its location's {@link ReorderPolicy}. The {@code ...ForUpdate} reads lock the row until the transaction
 * ends; a row just created is locked by its creating transaction anyway.
 */
@ApplicationScoped
public class StockTable {

    private static final String COLUMNS =
        "id, locationId, name, type, availableAmount, periodDemand, avgDemand, demandVar, minLevel, maxLevel";

    @Inject
    Db db;

    public StockRow create(Location location, String name, ProductType type) {
        var policy = ReorderPolicy.of(location);
        var estimate = DemandEstimate.initial(policy);
        var levels = LearnedLevels.of(estimate, policy);
        return db.queryOne("insert into stock (locationId, name, type, availableAmount, periodDemand, avgDemand,"
                + " demandVar, minLevel, maxLevel) values (?, ?, ?, 0, 0, ?, ?, ?, ?) returning " + COLUMNS,
            StockTable::map, location.id(), name, type, estimate.avg(), estimate.var(), levels.min(), levels.max())
            .orElseThrow();
    }

    public Optional<StockRow> findForUpdate(Location location, String name, ProductType type) {
        return db.queryOne("select " + COLUMNS + " from stock where locationId = ? and name = ? and type = ? for update",
            StockTable::map, location.id(), name, type);
    }

    public Optional<StockRow> findByNameForUpdate(Location location, String name) {
        return db.queryOne("select " + COLUMNS + " from stock where locationId = ? and name = ? for update",
            StockTable::map, location.id(), name);
    }

    /** The locked row, created if there is none yet. */
    public StockRow findOrCreateForUpdate(Location location, String name, ProductType type) {
        return findForUpdate(location, name, type).orElseGet(() -> create(location, name, type));
    }

    /** Without a lock. */
    public Optional<ProductType> findType(Location location, String name) {
        return db.queryOne("select type from stock where locationId = ? and name = ?",
            rs -> ProductType.valueOf(rs.getString("type")), location.id(), name);
    }

    public List<StockRow> findAll(Location location) {
        return db.query("select " + COLUMNS + " from stock where locationId = ? order by id", StockTable::map, location.id());
    }

    public List<StockRow> findAll() {
        return db.query("select " + COLUMNS + " from stock order by id", StockTable::map);
    }

    public StockRow addAvailable(long id, int delta) {
        return update("availableAmount = availableAmount + ?", delta, id);
    }

    public StockRow setAvailable(long id, int amount) {
        return update("availableAmount = ?", amount, id);
    }

    public void addPeriodDemand(long id, int quantity) {
        db.update("update stock set periodDemand = periodDemand + ? where id = ?", quantity, id);
    }

    /** Stores {@code estimate} and the levels derived from it, and starts a new demand period. */
    public void closePeriod(long id, DemandEstimate estimate, ReorderPolicy policy) {
        var levels = LearnedLevels.of(estimate, policy);
        db.update("update stock set avgDemand = ?, demandVar = ?, minLevel = ?, maxLevel = ?, periodDemand = 0 where id = ?",
            estimate.avg(), estimate.var(), levels.min(), levels.max(), id);
    }

    public void deleteAll() {
        db.update("delete from stock");
    }

    private StockRow update(String assignment, int value, long id) {
        return db.queryOne("update stock set " + assignment + " where id = ? returning " + COLUMNS, StockTable::map, value, id)
            .orElseThrow();
    }

    private static StockRow map(ResultSet rs) throws SQLException {
        return new StockRow(rs.getLong("id"), rs.getString("locationId"), rs.getString("name"),
            ProductType.valueOf(rs.getString("type")), rs.getInt("availableAmount"), rs.getInt("periodDemand"),
            rs.getDouble("avgDemand"), rs.getDouble("demandVar"), rs.getInt("minLevel"), rs.getInt("maxLevel"));
    }
}
