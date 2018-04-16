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
package org.nuxeo.ecm.core.io.avro;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.GregorianCalendar;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Field;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.impl.ListProperty;
import org.nuxeo.ecm.core.schema.types.ListType;
import org.nuxeo.runtime.RuntimeServiceException;
import org.nuxeo.runtime.avro.AvroDataFactory;
import org.nuxeo.runtime.avro.AvroDataFactoryService;
import org.nuxeo.runtime.avro.AvroSchemaFactoryContext;

/**
 * @since 10.2
 */
public class PropertyDataFactory extends AvroDataFactory<Property> {

    protected AvroDataFactoryService service;

    protected AvroSchemaFactoryContext context;

    public PropertyDataFactory(AvroDataFactoryService service, AvroSchemaFactoryContext context) {
        super();
        this.service = service;
        this.context = context;
    }

    @Override
    public Object createData(Schema schema, Property input) {
        switch (schema.getType()) {
        case UNION:
            for (Schema s : schema.getTypes()) {
                try {
                    return service.createData(s, input);
                } catch (NonNullValueException e) {
                    // ignore
                }
            }
            throw new RuntimeServiceException("cannot create value for " + schema.getType());
        case RECORD:
            if (input.isComplex()) {
                GenericRecord record = new GenericData.Record(schema);
                for (Field f : schema.getFields()) {
                    record.put(f.name(), service.createData(f.schema(), input.get(context.restoreForbidden(f.name()))));
                }
                return record;
            }
            throw new RuntimeServiceException("cannot create value for " + schema.getType());
        case ARRAY:
            if (input.getType().isListType()) {
                Collection<Object> objects;
                if (((ListType) input.getType()).isArray()) {
                    objects = Arrays.asList((Object[]) input.getValue());
                } else {
                    ListProperty list = (ListProperty) input;
                    objects = new ArrayList<>(list.size());
                    for (Property p : list) {
                        objects.add(service.createData(schema.getElementType(), p));
                    }
                }
                return new GenericData.Array<>(schema, objects);
            }
            throw new RuntimeServiceException("cannot create value for " + schema.getType());
        case INT:
        case BYTES:
        case FLOAT:
        case STRING:
        case DOUBLE:
        case BOOLEAN:
            if (input.isScalar()) {
                return input.getValue();
            }
            throw new RuntimeServiceException("cannot create value for " + schema.getType());
        case LONG:
            if (input.isScalar()) {
                String logicalType = schema.getProp("logicalType");
                if ("timestamp-millis".equals(logicalType)) {
                    GregorianCalendar cal = (GregorianCalendar) input.getValue();
                    return cal.toInstant().toEpochMilli();
                }
                return input.getValue();
            }
            throw new RuntimeServiceException("cannot create value for " + schema.getType());
        case NULL:
            if (input.getValue() == null) {
                return null;
            }
            throw new NonNullValueException();
        default:
            throw new RuntimeServiceException("not yet implemented : " + schema.getType());
        }
    }

}
