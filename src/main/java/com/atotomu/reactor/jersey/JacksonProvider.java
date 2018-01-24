package com.atotomu.reactor.jersey;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.type.ClassKey;
import com.fasterxml.jackson.databind.util.ClassUtil;
import com.fasterxml.jackson.databind.util.JSONPObject;
import com.sun.jersey.core.provider.AbstractMessageReaderWriterProvider;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.HashSet;

/**
 * @author wangtong
 * @since 1.0
 */
@Provider
@Consumes({MediaType.APPLICATION_JSON, "application/json;charset=UTF-8", "text/json"})
@Produces({MediaType.APPLICATION_JSON, "application/json;charset=UTF-8", "text/json"})
public class JacksonProvider extends AbstractMessageReaderWriterProvider<Object> {

    volatile ObjectMapper mapper = new ObjectMapper();

    protected String _jsonpFunctionName;
    protected HashSet<ClassKey> _cfgCustomUntouchables;
    protected boolean _cfgCheckCanSerialize = false;
    protected boolean _cfgCheckCanDeserialize = false;

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        if (!isJsonType(mediaType)) {
            return false;
        }

        /* Ok: looks like we must weed out some core types here; ones that
         * make no sense to try to bind from JSON:
         */
        if (_untouchables.contains(new ClassKey(type))) {
            return false;
        }
        // but some are interface/abstract classes, so
        for (Class<?> cls : _unwritableClasses) {
            if (cls.isAssignableFrom(type)) {
                return false;
            }
        }
        // and finally, may have additional custom types to exclude
        if (_containedIn(type, _cfgCustomUntouchables)) {
            return false;
        }

        // Also: if we really want to verify that we can deserialize, we'll check:
        if (_cfgCheckCanSerialize) {
            if (!locateMapper(type, mediaType).canSerialize(type)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        if (!isJsonType(mediaType)) {
            return false;
        }
         /* Ok: looks like we must weed out some core types here; ones that
         * make no sense to try to bind from JSON:
         */
        if (_untouchables.contains(new ClassKey(type))) {
            return false;
        }
        // and there are some other abstract/interface types to exclude too:
        for (Class<?> cls : _unreadableClasses) {
            if (cls.isAssignableFrom(type)) {
                return false;
            }
        }
        // as well as possible custom exclusions
        if (_containedIn(type, _cfgCustomUntouchables)) {
            return false;
        }
        // Finally: if we really want to verify that we can serialize, we'll check:
        if (_cfgCheckCanSerialize) {
            ObjectMapper mapper = locateMapper(type, mediaType);
            if (!mapper.canDeserialize(mapper.constructType(type))) {
                return false;
            }
        }
        return true;
    }

    ObjectMapper locateMapper(Class<?> type, MediaType mediaType) {
        return mapper;
    }

    @Override
    public Object readFrom(Class<Object> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws IOException, WebApplicationException {
        ObjectMapper _mapper = locateMapper(type, mediaType);
        JsonParser jp = _mapper.getFactory().createParser(entityStream);
        jp.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);
        return _mapper.readValue(jp, _mapper.constructType(genericType));
    }

    @Override
    public void writeTo(Object value, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
        ObjectMapper _mapper = locateMapper(type, mediaType);
        JsonGenerator jg = _mapper.getFactory().createGenerator(entityStream, JsonEncoding.UTF8);
        jg.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        JavaType rootType = null;
        if (genericType != null && value != null) {
            if (genericType.getClass() != Class.class) { // generic types are other impls of 'java.lang.reflect.Type'
                rootType = _mapper.getTypeFactory().constructType(genericType);
                if (rootType.getRawClass() == Object.class) {
                    rootType = null;
                }
            }
        }

        Class<?> viewToUse = null;
        if (annotations != null && annotations.length > 0) {
            viewToUse = _findView(_mapper, annotations);
        }
        if (viewToUse != null) {
            ObjectWriter viewWriter = _mapper.writerWithView(viewToUse);
            // [JACKSON-245] Allow automatic JSONP wrapping
            if (_jsonpFunctionName != null) {
                viewWriter.writeValue(jg, new JSONPObject(this._jsonpFunctionName, value, rootType));
            } else if (rootType != null) {
                _mapper.writerFor(rootType).withView(viewToUse).writeValue(jg, value);
            } else {
                viewWriter.writeValue(jg, value);
            }
        } else {
            if (_jsonpFunctionName != null) {
                _mapper.writeValue(jg, new JSONPObject(this._jsonpFunctionName, value, rootType));
            } else if (rootType != null) {
                _mapper.writerFor(rootType).writeValue(jg, value);
            } else {
                _mapper.writeValue(jg, value);
            }
        }
    }

    private Class<?> _findView(ObjectMapper mapper, Annotation[] annotations) throws JsonMappingException {
        for (Annotation annotation : annotations) {
            if (annotation.annotationType().isAssignableFrom(JsonView.class)) {
                JsonView jsonView = (JsonView) annotation;
                Class<?>[] views = jsonView.value();
                if (views.length > 1) {
                    StringBuilder s = new StringBuilder("Multiple @JsonView's can not be used on a JAX-RS method. Got ");
                    s.append(views.length).append(" views: ");
                    for (int i = 0; i < views.length; i++) {
                        if (i > 0) {
                            s.append(", ");
                        }
                        s.append(views[i].getName());
                    }
                    throw new JsonMappingException(s.toString());
                }
                return views[0];
            }
        }
        return null;
    }

    protected static boolean _containedIn(Class<?> mainType, HashSet<ClassKey> set) {
        if (set != null) {
            ClassKey key = new ClassKey(mainType);
            // First: type itself?
            if (set.contains(key)) {
                return true;
            }
            // Then supertypes (note: will not contain Object.class)
            for (Class<?> cls : ClassUtil.findSuperTypes(mainType, null)) {
                key.reset(cls);
                if (set.contains(key)) {
                    return true;
                }
            }
        }
        return false;
    }

    protected boolean isJsonType(MediaType mediaType) {
        if (mediaType != null) {
            // Ok: there are also "xxx+json" subtypes, which count as well
            String subtype = mediaType.getSubtype();
            return "json".equalsIgnoreCase(subtype) || subtype.endsWith("+json");
        }
        /* Not sure if this can happen; but it seems reasonable
         * that we can at least produce json without media type?
         */
        return true;
    }

    public final static HashSet<ClassKey> _untouchables = new HashSet<ClassKey>();

    static {
        // First, I/O things (direct matches)
        _untouchables.add(new ClassKey(InputStream.class));
        _untouchables.add(new ClassKey(Reader.class));
        _untouchables.add(new ClassKey(OutputStream.class));
        _untouchables.add(new ClassKey(Writer.class));

        // then some primitive types
        _untouchables.add(new ClassKey(byte[].class));
        _untouchables.add(new ClassKey(char[].class));
        // 24-Apr-2009, tatu: String is an edge case... let's leave it out
        _untouchables.add(new ClassKey(String.class));

        // Then core JAX-RS things
        _untouchables.add(new ClassKey(StreamingOutput.class));
        _untouchables.add(new ClassKey(Response.class));
    }

    /**
     * These are classes that we never use for reading
     * (never try to deserialize instances of these types).
     */
    public final static Class<?>[] _unreadableClasses = new Class<?>[]{
            InputStream.class, Reader.class
    };

    /**
     * These are classes that we never use for writing
     * (never try to serialize instances of these types).
     */
    public final static Class<?>[] _unwritableClasses = new Class<?>[]{
            OutputStream.class, Writer.class,
            StreamingOutput.class, Response.class
    };
}
