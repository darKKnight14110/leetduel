FROM bitnami/postgresql:18.6.0

USER 0
ARG PGVECTOR_VERSION=v0.8.1
RUN install_packages ca-certificates git build-essential postgresql-server-dev-18 \
    && git clone --depth 1 --branch ${PGVECTOR_VERSION} https://github.com/pgvector/pgvector.git /tmp/pgvector \
    && make -C /tmp/pgvector \
    && make -C /tmp/pgvector install \
    && rm -rf /tmp/pgvector
USER 1001
