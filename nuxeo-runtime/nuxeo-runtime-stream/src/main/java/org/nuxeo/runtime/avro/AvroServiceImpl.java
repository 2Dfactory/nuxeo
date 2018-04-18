/*
 * (C) Copyright 2018 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     pierre
 */
package org.nuxeo.runtime.avro;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.message.SchemaStore;
import org.nuxeo.runtime.RuntimeServiceException;

/**
 * @since 10.2
 */
public class AvroServiceImpl
        extends SchemaStore.Cache
        implements AvroService {

    private static final AvroDataFactory<Object> NULL_ADF = new AvroDataFactory<Object>(null) {
        @Override
        public GenericRecord createData(Schema schema, Object input) {
            return null;
        }
    };

    protected Map<Class<?>, AvroDataFactory<?>> dataFactories = new HashMap<>();

    protected Map<Class<?>, Class<AvroSchemaFactory<?>>> schemaFactories;

    protected final List<AvroReplacementDescriptor> replacements;

    public AvroServiceImpl(Collection<AvroReplacementDescriptor> replacements,
            Map<Class<?>, Class<AvroSchemaFactory<?>>> schemaFactories) {
        this.replacements = new ArrayList<>(replacements);
        this.schemaFactories = new HashMap<>(schemaFactories);
        // assert at creation that factories are valid
        createContext();
    }

    @Override
    public <T> Object createData(Schema schema, T input) {
        return getFactory(input).createData(schema, input);
    }

    @Override
    public <T> Schema createSchema(T input) {
        return createContext().createSchema(input);
    }

    @Override
    public String decodeName(String input) {
        String output = input;
        ListIterator<AvroReplacementDescriptor> it = replacements.listIterator(replacements.size());
        while (it.hasPrevious()) {
            AvroReplacementDescriptor replacement = it.previous();
            output = output.replaceAll(replacement.getReplacement(), replacement.getForbidden());
        }
        return output;
    }

    @Override
    public String encodeName(String input) {
        String output = input;
        for (AvroReplacementDescriptor replacement : replacements) {
            output = output.replaceAll(replacement.getForbidden(), replacement.getReplacement());
        }
        return output;
    }

    @Override
    public Schema findByName(String name) {
        throw new UnsupportedOperationException();
    }

    public void register(Class<?> type, AvroDataFactory<?> factory) {
        dataFactories.put(type, factory);
    }

    public void setFactories(Map<Class<?>, Class<AvroSchemaFactory<?>>> factories) {
        schemaFactories.clear();
        schemaFactories.putAll(factories);
    }

    protected AvroSchemaFactoryContext createContext() {
        AvroSchemaFactoryContext context = new AvroSchemaFactoryContext(this);
        for (Entry<Class<?>, Class<AvroSchemaFactory<?>>> entry : schemaFactories.entrySet()) {
            try {
                Class<AvroSchemaFactory<?>> clazz = entry.getValue();
                Constructor<AvroSchemaFactory<?>> constructor = clazz.getConstructor(AvroSchemaFactoryContext.class);
                AvroSchemaFactory<?> factory = constructor.newInstance(context);
                context.register(entry.getKey(), factory);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeServiceException(e);
            }
        }
        return context;
    }

    @SuppressWarnings("unchecked")
    protected <T> AvroDataFactory<T> getFactory(T input) {
        AvroDataFactory<T> factory = (AvroDataFactory<T>) dataFactories.get(input.getClass());
        if (factory != null) {
            return factory;
        }
        for (Class<?> intrface : input.getClass().getInterfaces()) {
            factory = (AvroDataFactory<T>) dataFactories.get(intrface);
            if (factory != null) {
                return factory;
            }
        }
        for (Entry<Class<?>, AvroDataFactory<?>> entry : dataFactories.entrySet()) {
            if (entry.getKey().isAssignableFrom(input.getClass())) {
                return (AvroDataFactory<T>) entry.getValue();
            }
        }
        return (AvroDataFactory<T>) NULL_ADF;
    }

}
