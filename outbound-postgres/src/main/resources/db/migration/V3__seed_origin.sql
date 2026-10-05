-- A supplier order can come from seeding the DC (SupplierOrderOrigin.SEED). V1's check constraint on the origin has a
-- generated name, so it is looked up by its definition, dropped and re-added with SEED.

do $$
declare
    c text;
begin
    select conname into c from pg_constraint
    where conrelid = 'supplier_order'::regclass and contype = 'c' and pg_get_constraintdef(oid) like '%origin%';
    execute format('alter table supplier_order drop constraint %I', c);
end $$;

alter table supplier_order add constraint supplier_order_origin_check
    check (origin in ('MANUAL', 'AUTOMATIC', 'SEED'));
