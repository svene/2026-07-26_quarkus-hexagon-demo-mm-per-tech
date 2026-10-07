-- The customers who paid at a till in the last demo day (store-occupancy), and every report of the last 30 minutes
-- per store for the charts on /locations (store-metrics). Like store_occupancy, not part of the admin reset - it is
-- the external system's state. The default only fills rows of older reports, which the next report overwrites.

alter table store_occupancy
    add column paid integer not null default 0;

create table store_occupancy_history (
    storeId varchar(255) not null,
    measuredAt timestamp(6) with time zone not null,
    inside integer not null,
    capacity integer not null,
    queuing integer not null,
    tills integer not null,
    tillsBusy integer not null,
    paid integer not null,
    turnedAway integer not null,
    primary key (storeId, measuredAt)
);
