-- Hibernate is gone (plain SQL over JDBC): the ids come from identity columns (INSERT ... RETURNING id) instead of the
-- Panache sequences. Each identity starts above every id the sequence can have handed out - Hibernate's pooled
-- optimizer reserved up to 50 ids per call - so no new row reuses an id that a Kafka message in flight refers to.

do $$
declare
    t text;
    next_id bigint;
begin
    foreach t in array array['stock', 'replenishment_request', 'shipment', 'supplier_order'] loop
        execute format('select last_value + 50 from %I', t || '_seq') into next_id;
        execute format('alter table %I alter column id add generated always as identity (start with %s)', t, next_id);
        execute format('drop sequence %I', t || '_seq');
    end loop;
end $$;
