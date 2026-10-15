FROM haskell@sha256:0dabe39ff7116832afd8219ce4ef9e7b35f5ba7bb779c83da83fa0195ee9c73f AS compiler
FROM ubuntu@sha256:534baea6a22c03a63003dbc8dbe78fe34bc0d7e595d9a9dc9834884ff530eb55
RUN apt-get update && apt-get install -y --no-install-recommends gcc libc6-dev libgmp-dev libtinfo6 && rm -rf /var/lib/apt/lists/*
COPY --from=compiler /opt/ghc /opt/ghc
ENV PATH="/opt/ghc/9.12.2/bin:${PATH}"
