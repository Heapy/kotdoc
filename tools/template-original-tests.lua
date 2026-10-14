-- Minimal test-runner adapter. The original test file and assertions are unchanged.
package.preload['tasty']=function()
  return {
    test_case=function(name,run) return {name=name,run=run} end,
    test_group=function(name) return function(children) return {name=name,children=children} end end,
    assert={
      are_equal=function(a,b) assert(a==b,tostring(a)..' /= '..tostring(b)) end,
      is_falsy=function(value) assert(not value,'expected falsy value') end,
      error_matches=function(run,pattern)
        local ok,err=pcall(run)
        assert(not ok and tostring(err):match(pattern),tostring(err))
      end,
    }
  }
end
function Writer(doc,opts)
  local tree=dofile(os.getenv('KOTDOC_ORIGINAL_TEMPLATE_TEST'))
  local names={}
  local function run(nodes,prefix)
    for _, node in ipairs(nodes) do
      local name=prefix..node.name
      if node.run then node.run();names[#names+1]=name
      else run(node.children,name..'/') end
    end
  end
  run(tree,'')
  return pandoc.json.encode(names)
end
