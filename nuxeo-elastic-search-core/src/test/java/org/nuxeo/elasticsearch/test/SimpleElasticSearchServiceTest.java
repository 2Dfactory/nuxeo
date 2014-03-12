/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Nuxeo
 */


package org.nuxeo.elasticsearch.test;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.QueryBuilders;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.elasticsearch.ElasticSearchComponent;
import org.nuxeo.elasticsearch.ElasticSearchService;
import org.nuxeo.elasticsearch.listener.ElasticsearchIndexingListener;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.google.inject.Inject;

@RunWith(FeaturesRunner.class)
@Features({RepositoryElasticSearchFeature.class})
public class SimpleElasticSearchServiceTest {

    @Inject
    protected CoreSession session;

    @Test
    public void shouldHaveDeclaredService() throws Exception {

        ElasticSearchService ess = Framework.getLocalService(ElasticSearchService.class);
        Assert.assertNotNull(ess);

        Client client = ess.getClient();
        Assert.assertNotNull(client);

        Assert.assertNotNull(session);
    }

    @Test
    public void shouldIndexDocuments() throws Exception {

        ElasticSearchService ess = Framework.getLocalService(ElasticSearchService.class);
        Assert.assertNotNull(ess);

        DocumentModel doc = session.createDocumentModel("/", "testDoc", "File");
        doc.setPropertyValue("dc:title", "TestMe");
        doc.putContextData(ElasticsearchIndexingListener.DISABLE_AUTO_INDEXING,
                Boolean.TRUE);
        doc = session.createDocument(doc);
        session.save();

        String res = ess.indexNow(doc);
        Assert.assertNotNull(res);

        SearchResponse searchResponse = ess.getClient().prepareSearch(ElasticSearchComponent.MAIN_IDX)
                //.setTypes("doc")
                .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                //.setQuery(QueryBuilders.termQuery("type", "File"))             // Query
                //.setQuery(QueryBuilders.termQuery("title", "Test Me"))             // Query
                //.setQuery(QueryBuilders.termQuery("dc:title", "Test"))             // Query
                //.setQuery(QueryBuilders.termQuery("uid", doc.getId()))             // Query
                .setFrom(0).setSize(60)
                .execute()
                .actionGet();
        System.out.println(searchResponse.getHits().getAt(0).sourceAsString());
        Assert.assertEquals(1, searchResponse.getHits().getTotalHits());

        searchResponse = ess.getClient().prepareSearch(ElasticSearchComponent.MAIN_IDX)
                .setTypes("doc")
                .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                .setQuery(QueryBuilders.fieldQuery("title", "TestMe"))
                .setFrom(0).setSize(60)
                .execute()
                .actionGet();
        Assert.assertEquals(1, searchResponse.getHits().getTotalHits());

        searchResponse = ess.getClient().prepareSearch(ElasticSearchComponent.MAIN_IDX)
                .setTypes("doc")
                .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                .setQuery(QueryBuilders.fieldQuery("properties.dc:title", "TestMe"))
                .setFrom(0).setSize(60)
                .execute()
                .actionGet();
        Assert.assertEquals(1, searchResponse.getHits().getTotalHits());

    }






}
