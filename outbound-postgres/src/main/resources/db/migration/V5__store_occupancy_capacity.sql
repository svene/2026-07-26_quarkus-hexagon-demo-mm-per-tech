-- The store's capacity and the customers turned away at the full store in the last demo day (store-occupancy).
-- A migration of its own: V4 may already be applied (dev database). The defaults only fill rows of older reports,
-- which the next report of their store overwrites.

alter table store_occupancy
    add column capacity integer not null default 1,
    add column turnedAway integer not null default 0;
