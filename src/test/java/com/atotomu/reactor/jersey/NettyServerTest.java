package com.atotomu.reactor.jersey;

import org.junit.Test;
import reactor.ipc.netty.http.server.HttpServer;

/**
 * @author wangtong
 * @since 1.0
 */
public class NettyServerTest {

    @Test
    public void test_start() {
        HttpServer.create(8080)
                .startAndAwait(JerseyBasedHandler.builder()
                        .withClassPath("com.atotomu.reactor.jersey.router")
                        .addValueProvider(JacksonProvider.class)
                        .build());

    }
}
