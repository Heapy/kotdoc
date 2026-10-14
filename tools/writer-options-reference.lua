local function plain(v)
 if type(v)=='userdata' then return tostring(v) end
 if type(v)=='table' then local out={};for k,x in pairs(v) do out[k]=plain(x) end;return out end
 return v
end
function observe(o)

 local fields={'chunk_template','cite_method','columns','dpi','email_obfuscation','split_level','epub_fonts','epub_title_page','epub_metadata','epub_subdirectory','extensions','highlight_method','html_math_method','link_images','html_q_tags','identifier_prefix','incremental','list_of_figures','list_of_tables','number_offset','number_sections','prefer_ascii','reference_doc','reference_links','reference_location','figure_caption_position','table_caption_position','section_divs','setext_headers','list_tables','slide_level','tab_stop','table_of_contents','template','toc_depth','top_level_division','variables','wrap_text'}
 local out={}
 for _,name in ipairs(fields) do
  local value=o[name]
  if value==nil then out[name]=pandoc.json.null
  elseif name=='template' then out[name]=true
  elseif name=='extensions' then local values={};for _,v in ipairs(value) do values[#values+1]=v end;table.sort(values);out[name]=values
  elseif name=='number_offset' or name=='epub_fonts' then local values={};for i=1,#value do values[i]=tostring(value[i]) end;out[name]=values
  elseif name=='variables' then out[name]=plain(value)
  elseif math.type(value)=='integer' then out[name]=tostring(value)
  else out[name]=value end
 end
 return out
end

function Writer(doc, opts)
 local f=assert(io.open(os.getenv('KOTDOC_WRITER_REQUEST')))
 local requests=pandoc.json.decode(f:read('*a'),false);f:close()
 local out={}
 for i,c in ipairs(requests) do
  local input=c.options or {}
  for _,key in ipairs({'columns','dpi','tab_stop','split_level','toc_depth','slide_level'}) do
   if input[key]~=nil then input[key]=tonumber(input[key]) end
  end
  if input.number_offset then for j=1,#input.number_offset do input.number_offset[j]=tonumber(input.number_offset[j]) end end
  local o=pandoc.WriterOptions(input)
  local row={record=observe(o)}
  if c.op=='native' then local ok,d=pcall(pandoc.read,c.input,'native');if not ok then error('case '..i..' '..(c.originalFile or 'sample')..': '..tostring(d)) end;row.text=pandoc.write(d,'native',o) end
  if c.op=='fb2' then row.text=pandoc.write(pandoc.read(c.input,'native'),'fb2',o) end
  if c.op=='template' then row.rendered=tostring(pandoc.template.apply(o.template,o.variables)) end
  out[i]=row
 end
 return pandoc.json.encode(out)
end
