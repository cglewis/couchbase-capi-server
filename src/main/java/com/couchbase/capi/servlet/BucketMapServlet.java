/**
 * Copyright (c) 2012 Couchbase, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.couchbase.capi.servlet;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.couchbase.capi.CouchbaseBehavior;

/**
 * This servlet is responsible for providing the bucket list and bucket details.
 *
 * Requests like:
 *
 * /.../buckets
 * AND
 * /.../buckets/default
 *
 * @author mschoch
 *
 */
@SuppressWarnings("serial")
public class BucketMapServlet extends HttpServlet {

    protected int numVbuckets = 1024;

    private static final Logger logger = LoggerFactory.getLogger(BucketMapServlet.class);
    protected ObjectMapper mapper = new ObjectMapper();

    protected CouchbaseBehavior couchbaseBehavior;

    public BucketMapServlet(CouchbaseBehavior couchbaseBehavior) {
        this.couchbaseBehavior = couchbaseBehavior;
    }

    public BucketMapServlet(CouchbaseBehavior couchbaseBehavior, int numVbuckets) {
        this.couchbaseBehavior = couchbaseBehavior;
        this.numVbuckets = numVbuckets;
    }

    /**
     * Handle get requests for the matching URLs and direct to the right handler method.
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String bucket = req.getPathInfo();
        OutputStream os = resp.getOutputStream();

        if (bucket == null || bucket.equals("/")) {
            String uuid = req.getParameter("uuid");
            executeBucketsRequest(resp, os, "default", uuid);
        } else {
            String bucketUUID = req.getParameter("bucket_uuid");
            bucket = getDatabaseNameFromPath(removePathSuffix(bucket, "/"));
            executeBucketRequest(resp, os, "default", bucket, bucketUUID);
        }
    }

    /**
     * Using the connection manager, find the client ids of the active connections
     * and return this as a list of a valid buckets.
     *
     * @param os
     * @throws IOException
     */
    protected void executeBucketsRequest(HttpServletResponse resp, OutputStream os, String pool, String uuid) throws IOException {
        logger.trace("asked for bucket list");

        List<Object> buckets = new ArrayList<Object>();

        List<String> bucketNames = couchbaseBehavior.getBucketsInPool(pool);

        if(uuid != null) {
            //if a uuid was provided make sure it matches for this pool
            String poolUUID = couchbaseBehavior.getPoolUUID(pool);
            if(!uuid.equals(poolUUID)) {
                resp.setStatus(404);
                os.write("Cluster uuid does not match the requested.".getBytes());
                os.close();
            } else {
                formatBuckets(resp, os, pool, buckets, bucketNames);
            }
        } else {
            formatBuckets(resp, os, pool, buckets, bucketNames);
        }
    }

    protected void formatBuckets(HttpServletResponse resp, OutputStream os, String pool,
            List<Object> buckets, List<String> bucketNames) throws IOException,
            JsonGenerationException, JsonMappingException {
        if(bucketNames != null) {
            for (String bucketName : bucketNames) {
                String actualBucketUUID = couchbaseBehavior.getBucketUUID(pool, bucketName);
                List<Object> nodes = couchbaseBehavior.getNodesServingPool(pool);
                Map<String, Object> bucket = buildBucketDetailsMap(bucketName, nodes, actualBucketUUID);
                buckets.add(bucket);
            }
            mapper.writeValue(os, buckets);
        } else {
            resp.setStatus(404);
        }
    }

    /**
     * Return a fake bucket map for the requested bucket.
     *
     * @param req
     * @param os
     * @param bucket
     * @throws IOException
     */
    protected void executeBucketRequest(HttpServletResponse resp, final OutputStream os,
            final String pool, final String bucket, String bucketUUID) throws IOException {

        String actualBucketUUID = couchbaseBehavior.getBucketUUID(pool, bucket);
        if(actualBucketUUID == null) {
            resp.setStatus(404);
            return;
        }

        List<Object> nodes = couchbaseBehavior.getNodesServingPool(pool);

        if(bucketUUID != null) {
            //if a bucket uuid is provided, make sure it matches the buckets uuid
            if(!bucketUUID.equals(actualBucketUUID)) {
                resp.setStatus(404);
                os.write("Bucket uuid does not match the requested.".getBytes());
                os.close();
            } else {
                formatBucket(resp, os, bucket, nodes, actualBucketUUID);
            }
        } else {
            formatBucket(resp, os, bucket, nodes, actualBucketUUID);
        }
    }

    protected void formatBucket(HttpServletResponse resp, final OutputStream os, final String bucket,
            List<Object> nodes, String actualBucketUUID) throws IOException, JsonGenerationException,
            JsonMappingException {

        if(nodes != null) {
            Map<String, Object> responseMap = buildBucketDetailsMap(bucket,
                    nodes, actualBucketUUID);

            mapper.writeValue(os, responseMap);
        } else {
            resp.setStatus(404);
        }
    }

    protected Map<String, Object> buildBucketDetailsMap(final String bucket,
            List<Object> nodes, String actualBucketUUID) {
        List<Object> serverList = new ArrayList<Object>();
        for (Object node : nodes) {
            Map<String, Object> nodeObj = (Map<String, Object>)node;
            serverList.add(nodeObj.get("hostname"));
            //add the bucket name to the node's couchApiBase
            String couchApiBase = (String)nodeObj.get("couchApiBase");
            nodeObj.put("couchApiBase", couchApiBase + bucket);
        }


        List<Object> vBucketMap = new ArrayList<Object>();
        for(int i=0; i < numVbuckets; i++) {
            List<Object> vbucket = new ArrayList<Object>();
            vbucket.add(i%serverList.size());
            vbucket.add(-1);
            vBucketMap.add(vbucket);
        }

        Map<String, Object> vbucketServerMap = new HashMap<String, Object>();
        vbucketServerMap.put("serverList", serverList);
        vbucketServerMap.put("vBucketMap", vBucketMap);

        Map<String, Object> responseMap = new HashMap<String, Object>();
        responseMap.put("nodes", nodes);
        responseMap.put("vBucketServerMap", vbucketServerMap);
        responseMap.put("name", bucket);
        responseMap.put("uri", String.format("/pools/default/buckets/%s?bucket_uuid=%s", bucket, actualBucketUUID));
        responseMap.put("uuid", actualBucketUUID);
        responseMap.put("bucketType", "membase");

        List<String> bucketCapabilities = new ArrayList<String>();
        bucketCapabilities.add("couchapi");
        responseMap.put("bucketCapabilities", bucketCapabilities);
        return responseMap;
    }

    protected String removePathSuffix(String path, String suffix) {
        if (path.endsWith(suffix)) {
            path = path.substring(0, path.length() - suffix.length());
        }
        return path;
    }

    protected String getDatabaseNameFromPath(String path) {
        String database = null;
        if(path.startsWith("/")) {
            database = path.substring(1);
        }
        return database;
    }

}
