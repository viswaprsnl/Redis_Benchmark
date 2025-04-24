import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Connection;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

public class MyClusterLoadTest {
    static List<int[]> DIST_LIST = new ArrayList<>();
    static final SecureRandom rnd = new SecureRandom();
    static final char[] ALPHANUM =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    static int pickSize() {
        int r = rnd.nextInt(100), cum = 0;
        for (int[] d : DIST_LIST) {
            cum += d[0];
            if (r < cum) return d[1];
        }
        return DIST_LIST.get(DIST_LIST.size() - 1)[1];
    }

    static String genValue(int n) {
        char[] buf = new char[n];
        for (int i = 0; i < n; i++) buf[i] = ALPHANUM[rnd.nextInt(ALPHANUM.length)];
        return new String(buf);
    }

    public static void main(String[] args) throws Exception {
        // defaults
        String host="127.0.0.1", password="";
        int port=0000, ops=500_000, threads=40, keyMax=1_000_000;
        int ttl=900, batch=10, connTimeout=2000, soTimeout=2000, maxAttempts=5;
        int poolMax=200, poolIdleMax=50, poolIdleMin=10;
        String dataSizeList="730000:1,4096:5,128:94";

        Iterator<String> it = Arrays.asList(args).iterator();
        while (it.hasNext()) {
            switch (it.next()) {
                case "--host"         -> host = it.next();
                case "--port"         -> port = Integer.parseInt(it.next());
                case "--password"     -> password = it.next();
                case "--ops"          -> ops = Integer.parseInt(it.next());
                case "--threads"      -> threads = Integer.parseInt(it.next());
                case "--keyMax"       -> keyMax = Integer.parseInt(it.next());
                case "--ttl"          -> ttl = Integer.parseInt(it.next());
                case "--batch"        -> batch = Integer.parseInt(it.next());
                case "--connTimeout"  -> connTimeout = Integer.parseInt(it.next());
                case "--soTimeout"    -> soTimeout = Integer.parseInt(it.next());
                case "--maxAttempts"  -> maxAttempts = Integer.parseInt(it.next());
                case "--poolMax"      -> poolMax = Integer.parseInt(it.next());
                case "--poolIdleMax"  -> poolIdleMax = Integer.parseInt(it.next());
                case "--poolIdleMin"  -> poolIdleMin = Integer.parseInt(it.next());
                case "--dataSizeList" -> dataSizeList = it.next();
                default -> {
                    System.err.println("Unknown arg");
                    System.exit(1);
                }
            }
        }

        DIST_LIST.clear();
        for (String part : dataSizeList.split(",")) {
            String[] kv = part.split(":");
            DIST_LIST.add(new int[]{Integer.parseInt(kv[1]), Integer.parseInt(kv[0])});
        }

        GenericObjectPoolConfig<Connection> pool = new GenericObjectPoolConfig<>();
        pool.setMaxTotal(poolMax); pool.setMaxIdle(poolIdleMax);
        pool.setMinIdle(poolIdleMin); pool.setTestOnBorrow(true);
        pool.setBlockWhenExhausted(true);

        HostAndPort seed = new HostAndPort(host, port);
        JedisCluster cluster = new JedisCluster(
            seed, connTimeout, soTimeout, maxAttempts, password, pool
        );

        System.out.println("PING → " + cluster.ping());

        ExecutorService exec = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        int perThread = ops / threads;
        final int finalPer = perThread, finalKeyMax = keyMax, finalTtl = ttl;
        final JedisCluster finalCluster = cluster;
        final String lua =
          "redis.call('MSET',KEYS[1],ARGV[1],KEYS[2],ARGV[2]);" +
          "redis.call('EXPIRE',KEYS[1],tonumber(ARGV[3]));" +
          "redis.call('EXPIRE',KEYS[2],tonumber(ARGV[3]));" +
          "return true;";

        long t0 = System.nanoTime();
        for (int t=0; t<threads; t++) {
            final int tid = t;
            exec.submit(() -> {
                try {
                    for (int i=0; i<finalPer; i++) {
                        int idx = tid*finalPer + i;
                        int kid = (idx % finalKeyMax) + 1;
                        String tag = "{" + kid + "}";
                        String k0 = "user:"+tag+":0", k1 = "user:"+tag+":1";
                        String v0 = genValue(pickSize()), v1 = genValue(pickSize());
                        try {
                            if (rnd.nextBoolean()) {
                                finalCluster.eval(lua,
                                  Arrays.asList(k0,k1),
                                  Arrays.asList(v0,v1,String.valueOf(finalTtl)));
                            } else {
                                finalCluster.setex(k0, finalTtl, v0);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        double secs = (System.nanoTime()-t0)/1e9;
        System.out.printf("Done %,d ops in %.2fs (%.2f ops/s)%n", ops, secs, ops/secs);

        exec.shutdown();
        cluster.close();
    }
}
EOF
