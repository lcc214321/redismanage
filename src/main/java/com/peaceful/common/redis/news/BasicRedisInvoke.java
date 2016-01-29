package com.peaceful.common.redis.news;

import com.peaceful.common.redis.proxy.JedisPoolService;
import com.peaceful.common.redis.proxy.JedisPoolServiceImpl;
import com.peaceful.common.redis.share.ShardJedisPoolService;
import com.peaceful.common.util.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisCommands;
import redis.clients.util.Pool;

import java.lang.reflect.Method;
import java.util.concurrent.*;

/**
 * 根据指定的集群节点，依次执行以下动作：获取对应集群的连接，执行命令，返回结果，并释放连接
 * <p/>
 * Created by wangjun on 16/1/29.
 */
public class BasicRedisInvoke implements RedisInvoke {

    Logger logger = LoggerFactory.getLogger(getClass());
    ShardJedisPoolService shardJedisPoolService = ShardJedisPoolService.getShardJedisPoolService();
    JedisPoolService jedisPoolService = JedisPoolServiceImpl.getJedisPoolService();


    @Override
    public Object doInvoke(Method method, Object[] args, int type, String node) throws TimeoutException {
        if (method.getDeclaredAnnotations().equals(Object.class)) {
            return null;
        }

        // 获取可用连接从连接池
        Pool pool;
        if (type == RedisClientType.PROXY) {
            pool = jedisPoolService.getJedisPoolByHostName(node);
        } else {
            pool = shardJedisPoolService.getShardJedisPoolByClusterName(node);
        }
        if (pool == null) {
            throw new RuntimeException(String.format("Error: can't get redis connection ,because of not connection pool of %s", node));
        }
        JedisCommands commands = (JedisCommands) pool.getResource();
        if (commands == null) {
            throw new RuntimeException("Error: can't get available connection from pool");
        }

        // 执行并返回结果然后归还连接
        try {
            return method.invoke(commands, args);
        } catch (Exception e) {
            pool.returnBrokenResource(commands);
            logger.error("Error:{}", ExceptionUtils.getStackTrace(e));
        } finally {
            pool.returnResource(commands);
        }

        return null;
    }


}
