-- The completed purchases of the stores and the online FC (store-purchases), for the Purchases table on /locations:
-- how many distinct products and how many units. Only the last 30 minutes per location are kept. App data, so the
-- admin reset deletes them, like the requests.

create table purchase (
    id bigint generated always as identity,
    locationId varchar(255) not null,
    products integer not null,
    units integer not null,
    purchasedAt timestamp(6) with time zone not null,
    primary key (id)
);

create index purchase_location_id on purchase (locationId, id);
