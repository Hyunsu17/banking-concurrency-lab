-- 락이 잡혀 있는 순간의 Redis 상태를 원자적으로 한 번에 뜬다.
-- KEYS/HGETALL을 따로 호출하면 그 사이에 락이 풀려 빈 결과가 나오므로 EVAL로 묶었다.
local keys = redis.call('keys', 'wallet:lock:*')
if #keys == 0 then return nil end

local key = keys[1]
local out = {
  'KEY   = ' .. key,
  'TYPE  = ' .. redis.call('type', key)['ok'],
  'ENC   = ' .. redis.call('object', 'encoding', key),
  'PTTL  = ' .. redis.call('pttl', key) .. ' ms',
  'HLEN  = ' .. redis.call('hlen', key),
}

local hash = redis.call('hgetall', key)
for i = 1, #hash, 2 do
  out[#out + 1] = 'FIELD = ' .. hash[i] .. '  ->  VALUE = ' .. hash[i + 1]
end
return out
