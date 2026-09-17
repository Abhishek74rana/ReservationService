-- Return held inventory, never exceeding the registered capacity.
--
-- KEYS[1] : inventory key
-- KEYS[2] : capacity key
-- ARGV[1] : quantity to return
--
-- The cap matters because the expiry sweeper and an explicit cancel could both
-- try to release the same hold after a crash. Clamping keeps a double release
-- from inventing inventory that does not exist.

local current = redis.call('GET', KEYS[1])
if current == false then
  return -1
end

local capacity = redis.call('GET', KEYS[2])
if capacity == false then
  return -1
end

local restored = tonumber(current) + tonumber(ARGV[1])
local cap = tonumber(capacity)

if restored > cap then
  restored = cap
end

redis.call('SET', KEYS[1], restored)
return restored
