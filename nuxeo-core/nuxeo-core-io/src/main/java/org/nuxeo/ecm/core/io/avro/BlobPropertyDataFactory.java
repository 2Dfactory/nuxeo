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

import java.nio.ByteBuffer;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Field;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.nuxeo.ecm.core.api.model.impl.primitives.BlobProperty;
import org.nuxeo.runtime.RuntimeServiceException;
import org.nuxeo.runtime.avro.AvroDataFactory;
import org.nuxeo.runtime.avro.AvroService;

/**
 * @since 10.2
 */
public class BlobPropertyDataFactory extends AvroDataFactory<BlobProperty> {

    public BlobPropertyDataFactory(AvroService service) {
        super(service);
    }

    @Override
    public Object createData(Schema schema, BlobProperty input) {
        switch (schema.getType()) {
        case NULL:
            if (input == null) {
                return null;
            }
            throw new RuntimeServiceException("cannot create value for " + schema.getType());
        case UNION:
            for (Schema s : schema.getTypes()) {
                try {
                    return service.createData(s, input);
                } catch (RuntimeServiceException e) {
                    // ignore
                }
            }
            throw new RuntimeServiceException("cannot create value for " + schema.getType());
        case RECORD:
            GenericRecord record = new GenericData.Record(schema);
            for (Field f : schema.getFields()) {
                if ("data".equals(f.name())) {
                    record.put(f.name(), ByteBuffer.wrap("data".getBytes()));
                } else {
                    record.put(f.name(), service.createData(f.schema(), input.get(service.decodeName(f.name()))));
                }
                input.getValueForWrite();
            }
            return record;
        default:
            throw new RuntimeServiceException("not yet implemented : " + schema.getType());
        }
    }

}
