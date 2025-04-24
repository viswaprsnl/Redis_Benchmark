# My Cluster Load Test

A simple, standalone Java load-testing tool for Redis Cluster, built on [Jedis](https://github.com/redis/jedis) and Apache Commons Pool.  
It supports:

- Atomic **MSET** + **EXPIRE** via Lua  
- Single-key **SETEX** updates  
- Customizable payload-size distributions  
- Per-key TTL  
- Connection pooling  
- Threaded execution  

---

## Prerequisites

- **Java 17 JDK**  
- **Maven 3.6+**  
- A running **Redis Cluster** (with at least one master node)

----------------------------------------------------------------------------------------------

## Building


# from project root
mvn clean package
Usage

This produces two artifacts in target/:

my-cluster-load-test-1.0.0.jar (thin, classes only)

my-cluster-load-test-1.0.0-shaded.jar (fat, bundled dependencies)


***********************************************************************************************
Run the fat JAR with -jar so it picks up the embedded Main-Class:

java -jar target/my-cluster-load-test-1.0.0-shaded.jar \
  --host       10.98.221.15 \
  --port       6410 \
  --password   yourRedisPassword \
  --ops        500000 \
  --threads    40 \
  --keyMax     1000000 \
  --ttl        900 \
  --batch      10 \
  --connTimeout 2000 \
  --soTimeout  2000 \
  --maxAttempts 5 \
  --poolMax    200 \
  --poolIdleMax 50 \
  --poolIdleMin 10 \
  --dataSizeList 730000:1,4096:5,128:94
CLI Options

Flag | Default | Description
--host | 127.0.0.1 | Redis node hostname or IP
--port | 0000 | Redis port
--password | (empty) | Redis AUTH password
--ops | 500000 | Total number of operations to perform
--threads | 40 | Number of worker threads
--keyMax | 1000000 | Maximum key index (keys cycle 1…keyMax)
--ttl | 900 | Expiration time in seconds for each key
--batch | 10 | (Currently unused – placeholder for future pipelining)
--connTimeout | 2000 | Connection timeout (ms)
--soTimeout | 2000 | Socket (read) timeout (ms)
--maxAttempts | 5 | Number of retries on MOVED/ASK errors
--poolMax | 200 | Max total connections in pool
--poolIdleMax | 50 | Max idle connections in pool
--poolIdleMin | 10 | Min idle connections in pool
--dataSizeList | 730000:1,4096:5,128:94 | Comma-separated <size>:<percent> to drive varied payloads
