-- The schema as Hibernate generated it before Flyway (2026-10-04, split-inventory incl. in-transit transfers).
-- Every later entity change needs a new migration V<n>__<what>.sql; Hibernate only validates the schema
-- (quarkus.hibernate-orm.database.generation=validate). Columns are unquoted, so Postgres stores them lower case.
-- Enum columns have check constraints: a new enum value needs a migration, too.

-- PanacheEntity ids: Hibernate fetches 50 ids per sequence call (pooled optimizer)
create sequence stock_SEQ start with 1 increment by 50;
create sequence replenishment_request_SEQ start with 1 increment by 50;
create sequence shipment_SEQ start with 1 increment by 50;
create sequence supplier_order_SEQ start with 1 increment by 50;

create table stock (
    id bigint not null,
    locationId varchar(255),
    name varchar(255),
    type varchar(255) check ((type in ('FRUIT','VEGETABLE','DAIRY','BEVERAGE','MEAT','BAKERY','NON_FOOD'))),
    availableAmount integer not null,
    periodDemand integer not null,
    avgDemand float(53) not null,
    demandVar float(53) not null,
    minLevel integer not null,
    maxLevel integer not null,
    primary key (id),
    unique (locationId, name, type)
);

create table replenishment_request (
    id bigint not null,
    locationId varchar(255),
    productName varchar(255),
    requested integer not null,
    shipped integer not null,
    status varchar(255) check ((status in ('PENDING','FULFILLED','REJECTED'))),
    origin varchar(255) check ((origin in ('MANUAL','AUTOMATIC'))),
    createdAt timestamp(6) with time zone,
    primary key (id)
);

create table shipment (
    id bigint not null,
    requestId bigint not null,
    locationId varchar(255),
    productName varchar(255),
    type varchar(255) check ((type in ('FRUIT','VEGETABLE','DAIRY','BEVERAGE','MEAT','BAKERY','NON_FOOD'))),
    quantity integer not null,
    status varchar(255) check ((status in ('IN_TRANSIT','ARRIVED'))),
    dispatchedAt timestamp(6) with time zone,
    arrivedAt timestamp(6) with time zone,
    primary key (id)
);

create table supplier_order (
    id bigint not null,
    productName varchar(255),
    type varchar(255) check ((type in ('FRUIT','VEGETABLE','DAIRY','BEVERAGE','MEAT','BAKERY','NON_FOOD'))),
    quantity integer not null,
    delivered integer not null,
    status varchar(255) check ((status in ('OPEN','DELIVERED','CANCELLED'))),
    origin varchar(255) check ((origin in ('MANUAL','AUTOMATIC'))),
    createdAt timestamp(6) with time zone,
    primary key (id)
);
