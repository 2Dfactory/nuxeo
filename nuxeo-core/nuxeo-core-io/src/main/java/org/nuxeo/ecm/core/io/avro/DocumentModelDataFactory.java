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

import org.apache.avro.Schema;
import org.apache.avro.Schema.Field;
import org.apache.avro.Schema.Type;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.runtime.avro.AvroDataFactory;
import org.nuxeo.runtime.avro.AvroService;

/**
 * @since 10.2
 */
public class DocumentModelDataFactory extends AvroDataFactory<DocumentModel> {

    public DocumentModelDataFactory(AvroService service) {
        super(service);
    }

    @Override
    public GenericRecord createData(Schema schema, DocumentModel input) {
        GenericRecord record = new GenericData.Record(schema);
        for (Field field : schema.getFields()) {
            String logicalType = field.schema().getProp("logicalType");
            if (field.schema().getType() == Type.RECORD && "schema".equals(logicalType)) {
                record.put(field.name(), service.createData(field.schema(), input));
            } else {
                Property p = input.getProperty(service.decodeName(field.name()));
                record.put(field.name(), service.createData(field.schema(), p));
            }
        }
        return record;
    }
}
