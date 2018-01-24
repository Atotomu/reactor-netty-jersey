package com.atotomu.reactor.jersey;

import com.sun.jersey.spi.container.WebApplication;

/**
 * @author wangtong
 * @since 1.0
 */
public class NettyContainer {

    private final WebApplication application;
    private final NettyToJerseyBridge nettyToJerseyBridge;

    public NettyContainer(WebApplication application) {
        this.application = application;
        nettyToJerseyBridge = new NettyToJerseyBridge(application);
    }

    NettyToJerseyBridge getNettyToJerseyBridge() {
        return nettyToJerseyBridge;
    }

    WebApplication getApplication() {
        return application;
    }
}