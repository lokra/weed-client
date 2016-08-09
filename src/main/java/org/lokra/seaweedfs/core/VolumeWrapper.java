package org.lokra.seaweedfs.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.lokra.seaweedfs.exception.SeaweedfsException;
import org.lokra.seaweedfs.exception.SeaweedfsFileNotFoundException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * @author Chiho Sin
 */
public class VolumeWrapper {

    private static final Log log = LogFactory.getLog(VolumeWrapper.class);

    private SystemConnection connection;
    private ObjectMapper objectMapper = new ObjectMapper();

    public VolumeWrapper(SystemConnection connection) {
        this.connection = connection;
    }

    /**
     * Upload file.
     *
     * @param url
     * @param fid
     * @param fileName
     * @param stream
     * @param ttl
     * @return The size returned is the size stored on SeaweedFS.
     * @throws IOException
     */
    public long uploadFile(String url, String fid, String fileName, InputStream stream, String ttl,
                           ContentType contentType)
            throws IOException {
        HttpPost request;
        if (ttl != null)
            request = new HttpPost(url + "/" + fid + "?ttl=" + ttl);
        else
            request = new HttpPost(url + "/" + fid);

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        builder.addBinaryBody("upload", stream, contentType, fileName);
        HttpEntity entity = builder.build();
        request.setEntity(entity);
        JsonResponse jsonResponse = connection.fetchJsonResultByRequest(request);
        convertResponseStatusToException(jsonResponse.statusCode, url, fid, false, false, false, false);
        return (Integer) objectMapper.readValue(jsonResponse.json, Map.class).get("size");
    }

    /**
     * Delete file.
     *
     * @param url
     * @param fid
     * @return The size returned is the trash size stored on SeaweedFS.
     * @throws IOException
     */
    public void deleteFile(String url, String fid) throws IOException {
        HttpDelete request = new HttpDelete(url + "/" + fid);
        convertResponseStatusToException(connection.fetchJsonResultByRequest(request).statusCode,
                url, fid, false, false, false, false);
    }

    /**
     * Check file is exist.
     *
     * @param url
     * @param fid
     * @return
     * @throws IOException
     */
    public boolean checkFileExist(String url, String fid) throws IOException {
        HttpHead request = new HttpHead(url + "/" + fid);
        final int statusCode = connection.fetchStatusCodeByRequest(request);
        try {
            convertResponseStatusToException(statusCode, url, fid, false, true, false, false);
            return true;
        } catch (SeaweedfsFileNotFoundException e) {
            return false;
        }
    }

    /**
     * Get file stream.
     *
     * @param url
     * @param fid
     * @return
     * @throws IOException
     */
    public StreamResponse getFileStream(String url, String fid) throws IOException {
        HttpGet request = new HttpGet(url + "/" + fid);
        StreamResponse cache = connection.fetchStreamCacheByRequest(request);
        convertResponseStatusToException(cache.getHttpResponseStatusCode(), url, fid, false, false, false, false);
        return cache;
    }

    /**
     * Get file status.
     *
     * @param url
     * @param fid
     * @return
     * @throws IOException
     */
    public HeaderResponse getFileStatus(String url, String fid) throws IOException {
        HttpHead request = new HttpHead(url + "/" + fid);
        HeaderResponse cache = connection.fetchHeaderByRequest(request);
        convertResponseStatusToException(cache.getHttpResponseStatusCode(), url, fid, false, false, false, false);
        return cache;
    }


    private void convertResponseStatusToException(int statusCode, String url, String fid,
                                                  boolean ignoreNotFound,
                                                  boolean ignoreRedirect,
                                                  boolean ignoreRequestError,
                                                  boolean ignoreServerError) throws SeaweedfsException {

        switch (((int) (statusCode / 100))) {
            case 1:
                return;
            case 2:
                return;
            case 3:
                if (ignoreRedirect)
                    return;
                throw new SeaweedfsException(
                        "fetch file from [" + url + "/" + fid + "] is redirect, " +
                                "response stats code is [" + statusCode + "]");
            case 4:
                if (statusCode == 404 && ignoreNotFound)
                    return;
                else if (statusCode == 404)
                    throw new SeaweedfsFileNotFoundException(
                            "fetch file from [" + url + "/" + fid + "] is not found, " +
                                    "response stats code is [" + statusCode + "]");
                if (ignoreRequestError)
                    return;
                throw new SeaweedfsException(
                        "fetch file from [" + url + "/" + fid + "] is request error, " +
                                "response stats code is [" + statusCode + "]");
            case 5:
                if (ignoreServerError)
                    return;
                throw new SeaweedfsException(
                        "fetch file from [" + url + "/" + fid + "] is request error, " +
                                "response stats code is [" + statusCode + "]");
            default:
                throw new SeaweedfsException(
                        "fetch file from [" + url + "/" + fid + "] is error, " +
                                "response stats code is [" + statusCode + "]");
        }
    }

}
