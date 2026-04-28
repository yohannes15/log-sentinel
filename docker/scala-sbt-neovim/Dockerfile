# Scala/sbt + Neovim + tree-sitter CLI. Built from repo root: docker/scala-sbt-neovim/Dockerfile
# ---------------------------------------------------------------------------
FROM sbtscala/scala-sbt:eclipse-temurin-17.0.15_6_1.12.8_3.8.2

USER root

# build-essential: cc/gcc for nvim-treesitter. unzip, ripgrep: checkhealth + tools that need rg/unzip.
RUN apt-get update && apt-get install -y --no-install-recommends sudo build-essential unzip ripgrep \
  && echo "sbtuser ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/sbtuser \
  && chmod 0440 /etc/sudoers.d/sbtuser \
  && mkdir -p /workspaces && chown sbtuser:sbtuser /workspaces \
  && rm -rf /var/lib/apt/lists/*

ARG NVIM_VERSION=0.12.2
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
  && arch="$(uname -m)" && \
  case "$arch" in x86_64) nv=nvim-linux-x86_64 ;; aarch64) nv=nvim-linux-arm64 ;; \
  *) echo "unsupported arch: $arch" >&2; exit 1 ;; esac && \
  curl -fsSL "https://github.com/neovim/neovim/releases/download/v${NVIM_VERSION}/${nv}.tar.gz" | tar -xz -C /opt && \
  ln -sf "/opt/${nv}/bin/nvim" /usr/local/bin/nvim && \
  rm -rf /var/lib/apt/lists/*

ARG TS_VERSION=0.26.8
RUN arch="$(uname -m)" && \
  case "$arch" in \
    x86_64) ts=tree-sitter-linux-x64 ;; \
    aarch64) ts=tree-sitter-linux-arm64 ;; \
    *) echo "unsupported arch: $arch" >&2; exit 1 ;; \
  esac && \
  curl -fsSL "https://github.com/tree-sitter/tree-sitter/releases/download/v${TS_VERSION}/${ts}.gz" -o /tmp/ts.gz && \
  gunzip -c /tmp/ts.gz > /usr/local/bin/tree-sitter && \
  rm -f /tmp/ts.gz && \
  chmod +x /usr/local/bin/tree-sitter

# Coursier (needed for nvim-metals to install/manage Metals itself)
# arm64 + amd64: both launchers are published for this Coursier tag.
ARG COURSIER_VERSION=v2.1.25-M24
USER root
RUN set -eux; \
  arch="$(uname -m)"; \
  case "$arch" in \
    x86_64) cspkg=cs-x86_64-pc-linux ;; \
    aarch64) cspkg=cs-aarch64-pc-linux ;; \
    *) echo "unsupported arch for Coursier: $arch" >&2; exit 1 ;; \
  esac; \
  curl -fL "https://github.com/coursier/coursier/releases/download/${COURSIER_VERSION}/${cspkg}.gz" | gunzip -c > /usr/local/bin/cs; \
  chmod +x /usr/local/bin/cs; \
  cs version; \
  command -v cs

USER sbtuser
WORKDIR /home/sbtuser
ENV PATH="/home/sbtuser/.local/share/coursier/bin:/usr/local/bin:/usr/bin:/bin"

WORKDIR /workspaces
