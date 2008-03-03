/*
 * (C) Copyright 2006-2007 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 *     <a href="mailto:at@nuxeo.com">Anahide Tchertchian</a>
 *
 * $Id: URIUtils.java 28167 2007-12-15 17:51:26Z sfermigier $
 */

package org.nuxeo.common.utils;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Helper class to parse a URI or build one given parameters.
 *
 * @author <a href="mailto:at@nuxeo.com">Anahide Tchertchian</a>
 */
public final class URIUtils {

    private static final Log log = LogFactory.getLog(URIUtils.class);

    // This is an utility class.
    private URIUtils() {
    }

    /**
     * Creates an URI query given the request parameters.
     *
     * @return an URI query given the request parameters.
     */
    public static String getURIQuery(Map<String, String> parameters) {
        String query = null;
        if (parameters != null) {
            try {
                List<String> items = new ArrayList<String>();
                for (Map.Entry<String, String> paramInfo : parameters.entrySet()) {
                    String key = paramInfo.getKey();
                    String value = paramInfo.getValue();
                    // XXX AT: see if needs encoding
                    if (key != null) {
                        if (value == null) {
                            value = "";
                        }
                        items.add(String.format("%s=%s", URLEncoder.encode(key,
                                "UTF-8"), URLEncoder.encode(value, "UTF-8")));
                    }
                }
                query = StringUtils.join(items, "&");
            } catch (UnsupportedEncodingException e) {
                log.error("Failed to get uri query", e);
            }
        }
        return query;
    }

    /**
     * Returns an URI path given the uri.
     */
    public static String getURIPath(String uri) {
        if (uri == null) {
            return null;
        }
        String path = uri;
        int index = uri.indexOf('?');
        if (index != -1) {
            path = uri.substring(0, index);
        }
        return path;
    }

    /**
     * @return a map with request parameters information given an URI query.
     */
    public static Map<String, String> getRequestParameters(String uriQuery) {
        Map<String, String> parameters = null;
        if (uriQuery != null && uriQuery.length() > 0) {
            try {
                String[] items = uriQuery.split("&");
                if (items != null && items.length > 0) {
                    parameters = new HashMap<String, String>();
                    for (String item : items) {
                        String[] param = item.split("=");
                        if (param != null) {
                            if (param.length == 2) {
                                parameters.put(URLDecoder.decode(param[0],
                                        "UTF-8"), URLDecoder.decode(param[1],
                                        "UTF-8"));
                            } else if (param.length == 1) {
                                parameters.put(URLDecoder.decode(param[0],
                                        "UTF-8"), null);
                            }
                        }
                    }
                }
            } catch (UnsupportedEncodingException e) {
                log.error("Failed to get request parameters from uri", e);
            }
        }
        return parameters;
    }

    public static String addParametersToURIQuery(String uriString,
            Map<String, String> parameters) {
        String res = uriString;
        try {
            String uriPath = getURIPath(uriString);
            URI uri = URI.create(uriString);
            String query = uri.getQuery();
            Map<String, String> existingParams = getRequestParameters(query);
            if (existingParams == null) {
                existingParams = new HashMap<String, String>();
            }
            existingParams.putAll(parameters);
            if (existingParams != null && !existingParams.isEmpty()) {
                String newQuery = getURIQuery(existingParams);
                res = uriPath + '?' + newQuery;
            } else {
                res = uriPath;
            }
        } catch (IllegalArgumentException e) {
            log.error("Failed to add new parameters to uri", e);
        }
        return res;
    }

}
