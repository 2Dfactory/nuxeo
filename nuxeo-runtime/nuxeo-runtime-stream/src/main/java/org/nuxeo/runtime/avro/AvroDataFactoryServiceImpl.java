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

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;

public class AvroDataFactoryServiceImpl implements AvroDataFactoryService {

    private static final AvroDataFactory<Object> NULL = new AvroDataFactory<Object>() {
        @Override
        public GenericRecord createData(Schema schema, Object input) {
            return null;
        }
    };

    protected Map<Class<?>, AvroDataFactory<?>> factories = new HashMap<>();

    @Override
    public <T> Object createData(Schema schema, T input) {
        return getFactory(input).createData(schema, input);
    }

    @SuppressWarnings("unchecked")
    protected <T> AvroDataFactory<T> getFactory(T input) {
        AvroDataFactory<T> factory = (AvroDataFactory<T>) factories.get(input.getClass());
        if (factory != null) {
            return factory;
        }
        for (Class<?> intrface : input.getClass().getInterfaces()) {
            factory = (AvroDataFactory<T>) factories.get(intrface);
            if (factory != null) {
                return factory;
            }
        }
        for (Entry<Class<?>, AvroDataFactory<?>> entry : factories.entrySet()) {
            if (entry.getKey().isAssignableFrom(input.getClass())) {
                return (AvroDataFactory<T>) entry.getValue();
            }
        }
        return (AvroDataFactory<T>) NULL;
    }

    @Override
    public void register(Class<?> type, AvroDataFactory<?> factory) {
        factories.put(type, factory);
    }

}
