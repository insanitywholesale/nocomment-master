# nocomment-master

## QnA

This is the central controller of nocom.

Q: "What is this?"
A: See [here](https://github.com/nerdsinspace/nocom-explanation/blob/main/README.md)

Q: "Where are the interesting parts?"
A: All of it is interesting! But here are some *especially* interesting parts:
* [The code that defined what areas were scanned on what schedule](src/main/java/nocomment/master/tracking/TrackyTrackyManager.java#L35-L67)
* [The Monte Carlo particle filter that actually tracked everyone](src/main/java/nocomment/master/tracking/MonteCarloParticleFilterMode.java#L192-L394)
* The remote base downloader [Part 1](src/main/java/nocomment/master/slurp/BlockCheckManager.java) and [Part 2](src/main/java/nocomment/master/slurp/SlurpManager.java)
* The clusterer, which took in a stream of hits and decided what was or wasn't a base [Part 1](src/main/java/nocomment/master/clustering/Aggregator.java) and [Part 2](src/main/java/nocomment/master/clustering/DBSCAN.java)
* [The code that associatied players with bases](src/main/java/nocomment/master/util/Associator.java#L53-L127)

Q: "How do I run this?"
A: You don't. It's far too heavily specialized for some 2b2t-specific behaviors, such as how long a chunk stays loaded after a player walks away, how large an area a player loads around themselves, how long a player can stay AFK without being kicked, etc. And regardless, the worker bot code is not being released, so you'd have to reverse engineer their connection protocol and guess how to make a functional AFK bot that speaks that language.

## Running

On Linux:

* Install JDK 11 (up to you to figure out)
* Run postgres (example using docker)
```bash
docker volume create nocom-psql-data
docker run --name nocom-psql -p 5432:5432 -v nocom-psql-data:/var/lib/postgresql/data -e POSTGRES_PASSWORD=zi0z11keqptzsbs -e POSTGRES_USER=nocom postgres:14 postgres -c log_statement=all
```
* Set variables that the program reads
```bash
export PSQL_USER=nocom; export PSQL_PASS=zi0z11keqptzsbs; export PSQL_URL=jdbc:postgresql://localhost:5432/nocom
```
* Initialize database
```bash
cat schema.sql | wl-copy
docker exec -it psql psql -U nocom
# Paste from clipboard and hit enter then exit
```
* Compile a jar that includes all dependencies
```bash
./gradlew shadowJar
```
* Run the program
```bash
java -jar build/libs/nocomment-master-1.0-SNAPSHOT-unoptimised.jar
```
* TODO
Figure out `No chat!` error here:
```bash
>>> rg 'No chat!'

src/main/java/nocomment/master/util/ChatProcessor.java
1023:                           System.out.println("No chat!");
```

## Files of import

* [Database setup](src/main/java/nocomment/master/db/Database.java)
* [Connection to bots through chat...???](src/main/java/nocomment/master/util/ChatProcessor.java)
