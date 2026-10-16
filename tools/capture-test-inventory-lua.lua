-- Development-only registration observer. Never invokes a source test callback.
-- Original script sources and their notices remain in the pinned submodule.
local root, config_path, output = assert(arg[1]), assert(arg[2]), assert(arg[3])
local json = require 'pandoc.json'
local function read(path)
  local file = assert(io.open(path, 'rb'))
  local value = file:read('a'); file:close(); return value
end
local scripts = json.decode(read(config_path))
local current_path
local function test(name, fn)
  if fn == nil then return function(f) return test(name, f) end end
  assert(type(name) == 'string' and type(fn) == 'function', 'unknown test registration shape')
  local info = debug.getinfo(fn, 'S')
  assert(info.source == '@' .. root .. '/' .. current_path, 'callback source is outside registered script')
  return {kind='test', name=name, first_line=info.linedefined, last_line=info.lastlinedefined}
end
local function group(name, children)
  if children == nil then return function(xs) return group(name, xs) end end
  assert(type(name) == 'string' and type(children) == 'table', 'unknown group registration shape')
  return {kind='group', name=name, children=children}
end
local forbidden = setmetatable({}, {__index=function()
  error('registration observer must not execute a test assertion')
end, __call=function() error('registration observer must not execute assertions') end})
package.loaded.tasty = nil
package.preload.tasty = function() return {test_case=test, test_group=group, assert=forbidden} end
local result = {oracle_version=tostring(PANDOC_VERSION), callbacks_executed=0, scripts={}}
for _, script in ipairs(scripts) do
  current_path = script.path
  local chunk = assert(loadfile(root .. '/' .. script.path))
  local tree = chunk()
  assert(type(tree) == 'table', 'script did not return a test list')
  result.scripts[#result.scripts+1] = {path=script.path, suite_id=script.id, tree=tree}
end
local file = assert(io.open(output, 'wb'))
file:write(json.encode(result)); file:close()
