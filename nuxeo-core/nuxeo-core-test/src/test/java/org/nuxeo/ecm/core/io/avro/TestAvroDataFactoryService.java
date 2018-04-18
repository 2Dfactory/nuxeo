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

import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;

import org.apache.avro.Schema;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.io.DatumWriter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.impl.primitives.BlobProperty;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.avro.AvroService;
import org.nuxeo.runtime.avro.AvroServiceImpl;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/**
 * @since 10.2
 */
@RunWith(FeaturesRunner.class)
@Features(CoreFeature.class)
@Deploy("org.nuxeo.ecm.core")
@Deploy("org.nuxeo.ecm.core.io")
@Deploy("org.nuxeo.ecm.core.schema")
@Deploy("org.nuxeo.runtime.stream")
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-repo-core-types-contrib.xml")
@RepositoryConfig(init = TestAvroDataFactoryServiceRepositoryInit.class)
public class TestAvroDataFactoryService {

    @Inject
    public AvroService service;

    @Inject
    public CoreSession session;

    @Test
    public void testDocumentFull() throws IOException {
        test("/myComplexDocFull");
    }

    @Test
    public void testDocumentPartial() throws IOException {
        test("/myComplexDocPartial");
    }

    protected void test(String documentPath) throws IOException {

        // temp test part
        ((AvroServiceImpl) service).register(Property.class, new PropertyDataFactory(service));
        ((AvroServiceImpl) service).register(BlobProperty.class, new BlobPropertyDataFactory(service));
        ((AvroServiceImpl) service).register(DocumentModel.class, new DocumentModelDataFactory(service));
        // end temp test part

        DocumentModel doc = session.getDocument(new PathRef(documentPath));
        assertNotNull(doc);
        Schema avro = service.createSchema(doc.getDocumentType());
        assertNotNull(avro);
        Object data = service.createData(avro, doc);

        File file = File.createTempFile("tutu", "testAvro" + documentPath.replaceAll("/", ""));

        DatumWriter<Object> datumWriter = new GenericDatumWriter<>(avro);
        DataFileWriter<Object> dataFileWriter = new DataFileWriter<>(datumWriter);
        dataFileWriter.create(avro, file);
        dataFileWriter.append(data);
        dataFileWriter.close();

    }

}
