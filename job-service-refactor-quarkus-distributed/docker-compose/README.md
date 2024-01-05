# Kogito and Infrastructure services

To allow a quick setup of all services required to run this demo, we provide a docker compose template that starts the following services:
- Postgresql
- PgAdmin
- Kogito Data Index
- Kogito Jobs Service
- Kogito Example Service (Only available if the example has been compiled using the `container` mvn profile eg: ```mvn cleanp package -Dcontainer```)
- Kogito Management Console
- Kogito Task Console
- Keycloak

> NOTE: In order to use it, please ensure you have Docker Compose installed on your machine, otherwise follow the instructions available
in [here](https://docs.docker.com/compose/install/).

## Starting the services

Use the `startServices.sh` passing the docker profile you want to use as an argument. 

Once the services are started (depending on the profile), the following ports will be assigned on your local machine:
- Postgresql: 5432
- PgAdmin: 8055
- Kogito Data Index: 8180
- Kogito Jobs: 8580
- Kogito Example Service: 8080
- Kogito Management Console: 8280
- Kogito Task Console: 8380
- Keycloak: 8480

## Stopping and removing volume data

To stop all services, simply run:

```shell
docker compose stop
```
or 

```shell
docker compose down 
```
to stop the services and remove the containers
docker-compose -f docker-compose-postgresql.yml stop

For more details please check the Docker Compose documentation.

```shell
docker compose --help
```
