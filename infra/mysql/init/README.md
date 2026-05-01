# MySQL Init Scripts

Place MySQL initialization SQL files in this directory.

The official MySQL container runs scripts from this directory only when the mysql-data volume is created for the first time. Use numbered prefixes such as `001_schema.sql` to keep execution order deterministic.

Database schema migrations for application tables should be added in Task 3. This directory is only the Docker entrypoint mount location for local development bootstrap scripts.
