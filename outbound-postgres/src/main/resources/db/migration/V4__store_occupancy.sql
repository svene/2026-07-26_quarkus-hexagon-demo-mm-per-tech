-- The latest occupancy each store's checkout system reported (store-occupancy topic): one row per store, overwritten
-- by newer reports only. Not part of the admin reset - it is the external system's state.

create table store_occupancy (
    storeId varchar(255) not null,
    measuredAt timestamp(6) with time zone not null,
    inside integer not null,
    queuing integer not null,
    tills integer not null,
    tillsBusy integer not null,
    primary key (storeId)
);
