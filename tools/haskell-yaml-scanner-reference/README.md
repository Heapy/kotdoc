# Original libyaml scanner reference

Compiles unchanged complete libyaml 0.2.5 api.c, reader.c and scanner.c.
The C bridge constructs byte/chunk inputs and observes tokens, marks, buffers,
pending keys, queue and errors after every actual yaml_parser_scan call.
Haskell transports JSON only. Native code is exclusively a test oracle.
