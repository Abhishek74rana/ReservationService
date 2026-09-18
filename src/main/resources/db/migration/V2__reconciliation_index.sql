-- Supports the reconciliation job, which asks "how many units are committed for
-- this item?" across the whole reservation table.
--
-- The query filters on status and inventory_id together, so a composite index on
-- those two columns serves it directly. Including quantity as a trailing column
-- makes it covering for this specific query — the planner can answer it from the
-- index alone without touching the heap.
--
-- The existing idx_reservation_inventory (inventory_id alone) remains useful for
-- other access patterns, so it is left in place rather than replaced.
CREATE INDEX idx_reservation_status_inventory_quantity
    ON reservation (status, inventory_id) INCLUDE (quantity);
