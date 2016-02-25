# Keycloak Benchmark

This is a stress test simulating users accessing fictive application that authorizes against one or more Keycloak servers, and admin users who change users' permissions etc.. There's no actual application and no container; the behaviour of particular `keycloak-adapter` is simulated within the test itself. For backchannel operations (such as logout notification) there's simple webserver (called AppServer) accepting requests from the server. ' Therefore SUT (system under test) covers only the Keycloak servers themselves, shared (PostgreSQL) database and the AppServer.

The test is driven by Gatling and also produces its report with response times statistics. To observe internal behaviour of the server, use of Java Flight Recorder as a low-impact profiler is recommended.

## Running the benchmark

After building the project the `target/dist/` directory will contain scripts, configuration files and test JARs for this test. You will directly use only script `bin/run.sh` which orchestrates the benchmark.

The benchmark is configurable using file passed as first argument to this script. The properties file **must** contain at least these variables:
- SERVERS = array with server addresses
- DRIVERS = array with driver addresses
- KEYCLOAK_DIST = location of keycloak server distribution

 Then, you can configure the database using variables DB_ADDRESS, DB_NAME, DB_USER and DB_PASSWORD. Note that PostgreSQL database must be set up separately (this script does not set up DB).

 Additional parameters configurable through the properties file are:
- LOADER_ARGS = any parameters passed to loader
- DRIVER_ARGS = any parameters passed to drivers
- RSH = remote shell command (default is ssh)
- RCP = remote copy command (default is scp)
- DC_DIR = directory for the domain controller
- LOG_DIR = directory for the logs
- SERVER_PORT = TCP port of server (defaults to 8080)
- APP_ADDRESS = AppServer address
- APP_PORT = AppServer port

There is an example configuration `test/dist/bin/example-properties.sh`.

The test will follow these steps:
- Copy necessary files (including Keycloak server distribution) onto server and driver (load generator) machines
- Start domain controller on local machine (where the script is executed)
- Start host controllers and Keycloak servers in clustered mode (using adapted HA-profile)
- Remove existing realm `benchmark-realm` from the server and create new one, with configurable number of users.
- Starts AppServer
- Run the actual Gatling test on all driver machines
- Retrieve `simulation.log` files from driver machines
- Create report from all drivers
- Kill all servers

Upon failure, servers are not killed. When the test is successfully finished, the Gatling report will be found in `/tmp/report`.

## Scenarios

Note that the Keycloak realm's user (username, credentials) is a different concept than the User below; here the user means a flow of operations on client/in application. Also, there are *active* and *inactive* Keycloak users; *inactive* users just occupy DB while *active users* is the set of credentials used in the *User scenario*. By *executing user* we mean a Keycloak user that is used in running *User scenario* instance.

To confuse things a bit more, Gatling reports 'active users' as well; there it means the number of currently running scenario instances (*User* or *Admin*).

- User: each user executes this scenario:
    - get login screen
    - type password incorrectly 0 - n times (constant probability of mistyping it on each attempt)
    - type password correctly
    - application authorizes against keycloak using OAuth
    - application refreshes token 0 - n times (constant probability of repeating this)
    - user logs out (with certain probability)
- Admin: each admin executes **one** of these operations:
    - add a new user, and set its password (that's second request)
    - remove existing user
    - list users whose username starts with string consisting of two random letters

There are pauses in between the User operations where appropriate, simulating User's think-time or application interaction not related to Keycloak. The actual pause is randomized with gaussian distribution and std.dev. equal to 1/10 of the mean value.

In order to not contend on this *active users*, each driver uses distinct subset of them, both for *User scenario* and *Admin* operations. Each *active user* is used by only single instance of *User scenario*; on the other hand, *Admin* operation can modify (=remove) both *executing* and non-executing (but active) user. When admin creates new user, it becomes an *active user*.

Every second, Gatling creates configurable number of User and Admin scenario instances (`test.usersPerSecond` or `test.adminsPerSecond`). The *executing user* becomes *active user* after the scenario instance is finished (either in a regular way or by an unexpected response from server).

Below is a table of options for the test (all these are to be found in `org.jboss.perf.Options`, those not listed below are injected automatically):

| Property                      | Default value | Description |
| :---------------------------- | ------------: | :---------- |
| test.rampUp                   | 30  | Period of load ramp-up, in seconds (going from 10% of users/admins per second to full value).
| test.duration                 |  60 | Full stress test duration, in seconds.
| test.rampDown                 |  30 | Time for executing users to die out, in seconds.
| test.usersPerSecond           | 100 | Number of user scenarios started each second.
| test.adminsPerSecond          |   2 | Number of admin scenarios started each second.
| test.activeUsers              |  50 | Active users used for the test.
| test.totalUsers               | 100 | All users in the database.
| test.usernameLength           |  16 | (Fixed) length of username, ASCII lower case characters.
| test.passwordLength           |  16 | (Fixed) password length, ASCII lower case characters.
| test.loginFailureProbability  | 0.2 | Probability of mistyping the password (value between 0 and 1).
| test.refreshTokenProbability  | 0.5 | Probability of asking for a token refresh (value between 0 and 1).
| test.logoutProbability        | 0.8 | Probability of executing of logout at the end of the scenario.
| test.userResponsePeriod       |   1 | Time to e.g. fill in login screen, in seconds.
| test.refreshTokenPeriod       |   3 | Time between token refresh requests, in seconds.
| test.addRemoveUserProbability | 0.2 | Probability of admin adding new user, or removing existing one. These are equal to keep balanced number of active users throughout the test.
| test.userRoles                | 150 | Number of distinct roles in the realm
| test.userRolesPerUser         |   5 | Number of roles one user has
| test.fullReload               | false | Applicable only to loader; reload whole realm (as opposed to only the active users)


