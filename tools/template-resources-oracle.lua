-- Observation adapter only: conversion/loading is performed by original Pandoc.
function Writer(doc, opts)
  local f=assert(io.open(os.getenv('KOTDOC_TEMPLATE_REQUEST')))
  local requests=pandoc.json.decode(f:read('*a'),false);f:close()
  local out={}
  local function bytes(hex) return hex:gsub('..',function(s) return string.char(tonumber(s,16)) end) end
  local function observe(c)
    pandoc.mediabag.empty()
    for _, item in ipairs(c.media or {}) do pandoc.mediabag.insert(item[1],nil,bytes(item[2])) end
    local ok, result=pcall(function()
      if c.op=='get' then return pandoc.template.get(c.path)
      elseif c.op=='default' then return pandoc.template.default(c.writer)
      elseif c.op=='decode' then
        pandoc.mediabag.insert(c.path,nil,bytes(c.hex))
        return pandoc.template.get(c.path)
      elseif c.op=='compile' or c.op=='compile-default' then
        local text=c.text
        local path=c.path
        if c.op=='compile-default' then
          text=pandoc.template.default(c.writer)
          path='templates/default.'..c.writer
        end
        local t=pandoc.template.compile(text,path)
        return pandoc.template.apply(t,c.context or {}):render(c.width)
      else error('unknown operation') end
    end)
    if ok then return {value=result} end
    local err=tostring(result)
    if c.http and err:match('Could not fetch ') then return {errorKind='http'} end
    return {error=err}
  end
  for _, c in ipairs(requests) do
    local options={resource_path=c.resourcePaths or {'resources'},user_data_dir=c.user and 'user' or 'empty-user'}
    out[#out+1]=pandoc.with_state(options,function() return observe(c) end)
  end
  return pandoc.json.encode(out)
end
