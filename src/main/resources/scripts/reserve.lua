-- Atomically decrement available inventory.
--
-- KEYS[1] : inventory key, e.g. inventory:room-101
-- ARGV[1] : quantity requested
--
-- Returns the remaining count on success, -1 if the key is unknown,
-- -2 if there is not enough inventory.
--
-- Redis executes a script as a single unit, so the read and the write cannot be
-- interleaved by another client. This is what prevents overbooking without
-- taking a distributed lock.

local current = redis.call('GET', KEYS[1])
if current == false then
  return -1
end

local available = tonumber(current)
local requested = tonumber(ARGV[1])

if available < requested then
  return -2
end

return redis.call('DECRBY', KEYS[1], requested)
