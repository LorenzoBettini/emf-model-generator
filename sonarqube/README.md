# Run the SonarQube analysis locally

## Ensure the system is ready for Docker

Ensure you installed Docker with Docker Compose.

In Linux, ensure that the `vm.max_map_count` is big enough (at least 262144):

```sh
sysctl vm.max_map_count
```

If it's lower, then increase it:

```sh
sudo sysctl -w vm.max_map_count=262144
```

## Start SonarQube inside a container

First, start SonarQube and its database with `docker-compose` from this directory:

```sh
docker compose up
```

Wait to see the line:

```text
sonarqube            | ... INFO  app[][o.s.a.SchedulerImpl] SonarQube is operational
```

The created user is `user` with password `userpass`.
If you need to login with administration privileges, the admin user is `admin` and its password is `admin` (you'll be forced to change that when you first login as an administrator).

Verify that you can access SonarQube by opening a browser on `http://localhost:9000` and login with the above credentials.

## Run the analysis from Maven

Retrieve the SonarQube user token:

When SonarQube is ready, retrieve the user token:

```sh
docker exec -it sonarqube cat /bootstrap/sonar-token.txt
```

Move to the main source folder of the project and run (replacing `<thetoken>` with the token shown by the previous command):

```sh
SONAR_TOKEN=<thetoken> ./mvnw -Pjacoco -Psonar-local verify sonar:sonar
```

When the build finises, examine the result of the analysis at `http://localhost:9000/projects`, opening the item corresponding to this project.