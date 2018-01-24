package com.atotomu.reactor.jersey;

import com.sun.jersey.core.header.InBoundHeaders;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerResponse;
import com.sun.jersey.spi.container.ContainerResponseWriter;
import com.sun.jersey.spi.container.WebApplication;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.ipc.netty.http.server.HttpServerRequest;
import reactor.ipc.netty.http.server.HttpServerResponse;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/**
 * @author wangtong
 * @since 1.0
 */
public class NettyToJerseyBridge {

    protected static final Logger logger = LoggerFactory.getLogger(NettyToJerseyBridge.class);

    private final WebApplication application;

    NettyToJerseyBridge(WebApplication application) {
        this.application = application;
    }

    ContainerRequest bridgeRequest(final HttpServerRequest nettyRequest, InputStream requestData) {
        try {
            URI baseUri = new URI("/"); // Since the netty server does not have a context path element as such, so base uri is always /
            URI uri = new URI(nettyRequest.uri());
            return new ContainerRequest(application, nettyRequest.method().name(),
                    baseUri, uri, new JerseyRequestHeadersAdapter(nettyRequest.requestHeaders()),
                    requestData);
        } catch (URISyntaxException e) {
            logger.error(String.format("Invalid request uri: %s", nettyRequest.uri()), e);
            throw new IllegalArgumentException(e);
        }
    }

    ContainerResponseWriter bridgeResponse(final HttpServerResponse serverResponse) {
        return new ContainerResponseWriter() {
            private final ByteBuf contentBuffer = serverResponse.alloc().buffer();

            @Override
            public OutputStream writeStatusAndHeaders(long contentLength, ContainerResponse response) {
                if (logger.isTraceEnabled()) {
                    logger.trace("entity = " + response.getEntity());
                }
                serverResponse.status(response.getStatus());
                HttpHeaders responseHeaders = new DefaultHttpHeaders();
                for (Map.Entry<String, List<Object>> header : response.getHttpHeaders().entrySet()) {
                    responseHeaders.add(header.getKey(), header.getValue());
                }
                serverResponse.headers(responseHeaders);
                return new ByteBufOutputStream(contentBuffer);
            }

            @Override
            public void finish() {
                if (logger.isTraceEnabled()) {
                    byte[] bytes = new byte[contentBuffer.readableBytes()];
                    contentBuffer.readBytes(bytes);
                    String str = new String(bytes, StandardCharsets.UTF_8);
                    logger.trace("send buffer = {}", str);
                }
                CountDownLatch latch = new CountDownLatch(1);
                serverResponse.send(Mono.just(contentBuffer)).subscribe(new Subscriber<Void>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                    }

                    @Override
                    public void onNext(Void aVoid) {
                    }

                    @Override
                    public void onError(Throwable t) {
                        logger.error("send msg error", t);
                        latch.countDown();
                    }

                    @Override
                    public void onComplete() {
                        latch.countDown();
                    }
                });

                try {
                    latch.await();
                } catch (InterruptedException e) {
                    logger.error("send msg error", e);
                }
                logger.trace("send finish ......");
            }
        };
    }

    private static class JerseyRequestHeadersAdapter extends InBoundHeaders {

        private static final long serialVersionUID = 2303297923762115950L;

        private final HttpHeaders requestHeaders;
        private Set<Map.Entry<String, List<String>>> entrySet;
        private Collection<List<String>> values;

        private JerseyRequestHeadersAdapter(HttpHeaders requestHeaders) {
            this.requestHeaders = requestHeaders;
        }

        @Override
        public void putSingleObject(String key, Object value) {
            throw new UnsupportedOperationException("No modifications allowed on request headers."); // The API is sad
        }

        @Override
        public void addObject(String key, Object value) {
            throw new UnsupportedOperationException("No modifications allowed on request headers."); // The API is sad
        }

        @Override
        public <A> List<A> get(String key, Class<A> type) {
            if (!type.isAssignableFrom(String.class)) {
                return Collections.emptyList();
            }
            @SuppressWarnings("unchecked")
            List<A> values = (List<A>) requestHeaders.getAll(key);
            return values;
        }

        @Override
        public <A> A getFirst(String key, Class<A> type) {
            List<A> values = get(key, type);
            return null != values && !values.isEmpty() ? values.get(0) : null;
        }

        @Override
        public <A> A getFirst(String key, A defaultValue) {
            @SuppressWarnings("unchecked")
            A value = (A) getFirst(key, defaultValue.getClass());
            return null != value ? value : defaultValue;
        }

        @Override
        public void putSingle(String key, String value) {
            throw new UnsupportedOperationException("No modifications allowed on request headers."); // The API is sad
        }

        @Override
        public void add(String key, String value) {
            throw new UnsupportedOperationException("No modifications allowed on request headers."); // The API is sad
        }

        @Override
        public String getFirst(String key) {
            return getFirst(key, String.class);
        }

        @Override
        protected List<String> getList(String key) {
            return get(key, String.class);
        }

        @Override
        public boolean containsValue(Object value) {
            List<Map.Entry<String, String>> entries = requestHeaders.entries();
            for (Map.Entry<String, String> entry : entries) {
                if (value.equals(entry.getValue())) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public List<String> get(Object key) {
            return getList(String.valueOf(key));
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException("No modifications allowed on request headers."); // The API is sad
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<String>> eldest) {
            throw new UnsupportedOperationException("No modifications allowed on request headers."); // The API is sad
        }

        @Override
        public int size() {
            return requestHeaders.names().size();
        }

        @Override
        public boolean isEmpty() {
            return requestHeaders.names().isEmpty();
        }

        @Override
        public boolean containsKey(Object key) {
            return requestHeaders.contains(String.valueOf(key));
        }

        @Override
        public List<String> put(String key, List<String> value) {
            throw new UnsupportedOperationException("No modifications allowed on request headers.");
        }

        @Override
        public void putAll(Map<? extends String, ? extends List<String>> m) {
            throw new UnsupportedOperationException("No modifications allowed on request headers.");
        }

        @Override
        public List<String> remove(Object key) {
            throw new UnsupportedOperationException("No modifications allowed on request headers.");
        }

        @Override
        public synchronized Set<Map.Entry<String, List<String>>> entrySet() {
            if (null != entrySet) {
                return entrySet;
            }
            List<Map.Entry<String, String>> entries = requestHeaders.entries();
            entrySet = new HashSet<Map.Entry<String, List<String>>>(entries.size());
            for (final Map.Entry<String, String> entry : entries) {
                ArrayList<String> listValue = new ArrayList<String>();
                listValue.add(entry.getValue());
                entrySet.add(new SimpleEntry<String, List<String>>(entry.getKey(), listValue));
            }
            return entrySet;
        }

        @Override
        public Set<String> keySet() {
            return requestHeaders.names();
        }

        @Override
        public synchronized Collection<List<String>> values() {
            if (null != values) {
                return values;
            }

            values = new ArrayList<List<String>>();
            for (String headerName : requestHeaders.names()) {
                values.add(requestHeaders.getAll(headerName));
            }
            return values;
        }

        @Override
        public boolean equals(Object o) {
            return requestHeaders.equals(o);
        }

        @Override
        public int hashCode() {
            return requestHeaders.hashCode();
        }

        @Override
        public String toString() {
            return requestHeaders.toString();
        }
    }
}
