#!/bin/bash
# Runs once on first start of the postgres container.
# One database + one least-privilege role per service: a compromised service cannot read another's data.
set -e
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<EOSQL
CREATE ROLE auth_svc    LOGIN PASSWORD '${AUTH_DB_PASSWORD}';
CREATE ROLE product_svc LOGIN PASSWORD '${PRODUCT_DB_PASSWORD}';
CREATE ROLE order_svc   LOGIN PASSWORD '${ORDER_DB_PASSWORD}';

CREATE DATABASE authdb    OWNER auth_svc;
CREATE DATABASE productdb OWNER product_svc;
CREATE DATABASE orderdb   OWNER order_svc;

REVOKE ALL ON DATABASE authdb    FROM PUBLIC;
REVOKE ALL ON DATABASE productdb FROM PUBLIC;
REVOKE ALL ON DATABASE orderdb   FROM PUBLIC;
EOSQL
