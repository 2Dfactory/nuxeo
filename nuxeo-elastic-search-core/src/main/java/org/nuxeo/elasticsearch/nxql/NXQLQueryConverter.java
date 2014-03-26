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
 *     Tiry
 *     bdelbosc
 */

package org.nuxeo.elasticsearch.nxql;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.nuxeo.ecm.core.api.SortInfo;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.ecm.core.query.sql.SQLQueryParser;
import org.nuxeo.ecm.core.query.sql.model.DefaultQueryVisitor;
import org.nuxeo.ecm.core.query.sql.model.Expression;
import org.nuxeo.ecm.core.query.sql.model.FromClause;
import org.nuxeo.ecm.core.query.sql.model.FromList;
import org.nuxeo.ecm.core.query.sql.model.Literal;
import org.nuxeo.ecm.core.query.sql.model.LiteralList;
import org.nuxeo.ecm.core.query.sql.model.MultiExpression;
import org.nuxeo.ecm.core.query.sql.model.Operand;
import org.nuxeo.ecm.core.query.sql.model.Operator;
import org.nuxeo.ecm.core.query.sql.model.OrderByClause;
import org.nuxeo.ecm.core.query.sql.model.OrderByExpr;
import org.nuxeo.ecm.core.query.sql.model.OrderByList;
import org.nuxeo.ecm.core.query.sql.model.Reference;
import org.nuxeo.ecm.core.query.sql.model.SQLQuery;
import org.nuxeo.ecm.core.query.sql.model.SelectClause;
import org.nuxeo.ecm.core.storage.sql.jdbc.NXQLQueryMaker;

/**
 * Helper class that holds the conversion logic.
 *
 * Conversion is based on the existing NXQL Parser, we are just using a visitor
 * to build the ES request.
 *
 */
public class NXQLQueryConverter {

    /**
     * Class to hold both a query and a filter
     *
     */
    public static class QueryAndFilter {

        public QueryBuilder query;
        public FilterBuilder filter;

        public QueryAndFilter(QueryBuilder query, FilterBuilder filter) {
            this.query = query;
            this.filter = filter;
        }
    }

    public static class ExpressionBuilder {

        public String operator;
        public QueryBuilder query;

        public ExpressionBuilder(final String op) {
            this.operator = op;
            this.query = null;
        }

        public void add(final QueryAndFilter qf) {
            if (qf != null)
                add(qf.query, qf.filter);
        }

        public void add(QueryBuilder q) {
            add(q, null);
        }

        public void add(final QueryBuilder q, final FilterBuilder f) {
            if (q == null && f == null) {
                return;
            }
            QueryBuilder inputQuery = q;
            if (inputQuery == null) {
                inputQuery = QueryBuilders.constantScoreQuery(f);
            }
            if (operator == null) {
                // first level expression
                query = inputQuery;
            } else {
                // boolean query
                if (query == null) {
                    query = QueryBuilders.boolQuery();
                }
                BoolQueryBuilder boolQuery = (BoolQueryBuilder) query;
                if ("AND".equals(operator)) {
                    boolQuery.must(inputQuery);
                } else if ("OR".equals(operator)) {
                    boolQuery.should(inputQuery);
                } else if ("NOT".equals(operator)) {
                    boolQuery.mustNot(inputQuery);
                }
            }
        }

        public void merge(ExpressionBuilder expr) {
            if ((expr.operator == operator) && (query == null)) {
                query = expr.query;
            } else {
                add(new QueryAndFilter(expr.query, null));
            }
        }

        public QueryBuilder get() {
            if (query == null) {
                return QueryBuilders.matchAllQuery();
            }
            return query;
        }

        @Override
        public String toString() {
            return query.toString();
        }

    }

    public static QueryBuilder toESQueryBuilder(String nxql) {
        final LinkedList<ExpressionBuilder> builders = new LinkedList<ExpressionBuilder>();
        SQLQuery nxqlQuery = SQLQueryParser.parse(new StringReader(nxql));
        final ExpressionBuilder ret = new ExpressionBuilder(null);
        builders.add(ret);
        final ArrayList<String> fromList = new ArrayList<String>();
        nxqlQuery.accept(new DefaultQueryVisitor() {

            private static final long serialVersionUID = 1L;

            @Override
            public void visitQuery(SQLQuery node) {
                super.visitQuery(node);
                // intentionally does not set limit or offset in the query
            }

            @Override
            public void visitFromClause(FromClause node) {
                FromList elements = node.elements;
                for (int i = 0; i < elements.size(); i++) {
                    String type = elements.get(i);
                    fromList.add(type);
                    if (NXQLQueryMaker.TYPE_DOCUMENT.equalsIgnoreCase(type)) {
                        // From Document means all doc types
                        fromList.clear();
                        return;
                    }
                }
            }

            @Override
            public void visitMultiExpression(MultiExpression node) {
                for (Iterator<Operand> it = node.values.iterator(); it
                        .hasNext();) {
                    it.next().accept(this);
                    if (it.hasNext()) {
                        node.operator.accept(this);
                    }
                }
            }

            @Override
            public void visitSelectClause(SelectClause node) {
                // NOP
            }

            @Override
            public void visitExpression(Expression node) {
                Operator op = node.operator;
                if (op == Operator.AND || op == Operator.OR
                        || op == Operator.NOT) {
                    builders.add(new ExpressionBuilder(op.toString()));
                    super.visitExpression(node);
                    ExpressionBuilder expr = builders.removeLast();
                    if (!builders.isEmpty()) {
                        builders.getLast().merge(expr);
                    }
                } else {
                    Reference ref = node.lvalue instanceof Reference ? (Reference) node.lvalue
                            : null;
                    String name = ref != null ? ref.name : node.lvalue
                            .toString();
                    String value = null;
                    try {
                        value = ((Literal) node.rvalue).asString();
                    } catch (Throwable e) {
                        if (node.rvalue != null) {
                            value = node.rvalue.toString();
                        }
                    }
                    Object[] values = null;
                    if (node.rvalue instanceof LiteralList) {
                        LiteralList items = (LiteralList) node.rvalue;
                        values = new Object[items.size()];
                        int i = 0;
                        for (Literal item : items) {
                            values[i++] = item.asString();
                        }
                    }
                    // add expression to the last builder
                    builders.getLast().add(
                            makeQueryFromSimpleExpression(op.toString(), name,
                                    value, values));
                }
            }
        });
        QueryBuilder queryBuilder = ret.get();
        if (!fromList.isEmpty()) {
            return QueryBuilders.filteredQuery(
                    queryBuilder,
                    makeQueryFromSimpleExpression("IN", "ecm:primarytype",
                            null, fromList.toArray()).filter);
        }
        return queryBuilder;
    }

    public static QueryAndFilter makeQueryFromSimpleExpression(String op,
            String name, Object value, Object[] values) {
        QueryBuilder query = null;
        FilterBuilder filter = null;
        if ("=".equals(op) || "!=".equals(op) || "<>".equals(op)) {
            // TODO: Remove hardcoded fields that requires a
            // fulltext analyzer
            if ("dc:title".equals(name) || NXQL.ECM_FULLTEXT.equals(name)) {
                query = QueryBuilders.matchQuery(name, value).operator(
                        MatchQueryBuilder.Operator.AND);
            } else {
                filter = FilterBuilders.termFilter(name, value);
            }
            if (!"=".equals(op)) {
                if (filter != null) {
                    filter = FilterBuilders.notFilter(filter);
                } else {
                    filter = FilterBuilders.notFilter(FilterBuilders
                            .queryFilter(query));
                    query = null;
                }
            }
        } else if ("LIKE".equals(op) || "ILIKE".equals(op)) {
            // Note that ILIKE will work only with a correct mapping
            query = QueryBuilders.regexpQuery(name,
                    ((String) value).replace('%', '*'));
        } else if ("BETWEEN".equals(op)) {
            filter = FilterBuilders.rangeFilter(name).from(values[0])
                    .to(values[1]);
        } else if ("IN".equals(op)) {
            filter = FilterBuilders.inFilter(name, values);
        } else if ("STARTSWITH".equals(op)) {
            if (name != null && name.equals(NXQL.ECM_PATH)) {
                filter = FilterBuilders.termFilter(name + ".children", value);
            } else {
                filter = FilterBuilders.prefixFilter(name, (String) value);
            }
        } else if (NXQL.ECM_FULLTEXT.equals(name)) {
            query = QueryBuilders.matchQuery("_all", value)
                    .operator(MatchQueryBuilder.Operator.AND)
                    .analyzer("fulltext");
        } else if (">".equals(op)) {
            filter = FilterBuilders.rangeFilter(name).gt(value);
        } else if ("<".equals(op)) {
            filter = FilterBuilders.rangeFilter(name).lt(value);
        } else if (">=".equals(op)) {
            filter = FilterBuilders.rangeFilter(name).gte(value);
        } else if ("<=".equals(op)) {
            filter = FilterBuilders.rangeFilter(name).lte(value);
        } else if ("IS NULL".equals(op)) {
            filter = FilterBuilders.missingFilter(name).nullValue(true);
        } else if ("IS NOT NULL".equals(op)) {
            filter = FilterBuilders.existsFilter(name);
        }
        return new QueryAndFilter(query, filter);
    }


    public static List<SortInfo> getSortInfo(String nxql) {

        final List<SortInfo> sortInfos = new ArrayList<>();

        SQLQuery nxqlQuery = SQLQueryParser.parse(new StringReader(nxql));
        nxqlQuery.accept(new DefaultQueryVisitor() {

            private static final long serialVersionUID = 1L;

            @Override
            public void visitOrderByExpr(OrderByExpr node) {
                sortInfos.add(new SortInfo(node.reference.name, !node.isDescending));
            }

        });

        return sortInfos;
    }
}
