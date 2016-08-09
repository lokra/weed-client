package org.lokra.seaweedfs;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.entity.ContentType;
import org.lokra.seaweedfs.cache.HeaderCache;
import org.lokra.seaweedfs.cache.StreamCache;
import org.lokra.seaweedfs.core.*;
import org.lokra.seaweedfs.core.contect.*;
import org.lokra.seaweedfs.exception.*;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.TimeZone;

/**
 * @author Chiho Sin
 */
public class FileTemplate implements InitializingBean, DisposableBean {

    private static final Log log = LogFactory.getLog(FileTemplate.class);
    private static final SimpleDateFormat headerDateFormat =
            new SimpleDateFormat("EEE',' dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);


    private MasterWrapper masterWrapper;
    private VolumeWrapper volumeWrapper;

    private int sameRackCount = 0;
    private int diffRackCount = 0;
    private int diffDataCenterCount = 0;
    private String replicationFlag = "000";
    private String timeToLive = null;
    private String dataCenter = null;
    private String collection = null;
    private boolean usingPublicUrl = true;
    private boolean loadBalance = true;
    private AssignFileKeyParams assignFileKeyParams = new AssignFileKeyParams();

    public FileTemplate(SystemConnection connection) {
        this.masterWrapper = new MasterWrapper(connection);
        this.volumeWrapper = new VolumeWrapper(connection);
        headerDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    /**
     * Save a file.
     *
     * @param fileName file name, that can be gzipped based on the file name extension and zip it.
     * @param stream   file stream.
     * @return File status.
     * @throws IOException
     */
    public FileHandleStatus saveFileByStream(String fileName, InputStream stream) throws IOException {
        return saveFileByStream(fileName, stream, ContentType.DEFAULT_BINARY);
    }

    /**
     * Save a file.
     *
     * @param fileName    file name, that can be gzipped based on the file name extension and zip it.
     * @param stream      file stream.
     * @param contentType file content type.
     * @return File status.
     * @throws IOException
     */
    public FileHandleStatus saveFileByStream(String fileName, InputStream stream, ContentType contentType)
            throws IOException {
        // Assign file key
        final AssignFileKeyResult assignFileKeyResult =
                masterWrapper.assignFileKey(assignFileKeyParams);
        String uploadUrl;
        if (usingPublicUrl)
            uploadUrl = assignFileKeyResult.getPublicUrl();
        else
            uploadUrl = assignFileKeyResult.getUrl();

        // Upload file
        return new FileHandleStatus(
                assignFileKeyResult.getFid(),
                volumeWrapper.uploadFile(
                        uploadUrl,
                        assignFileKeyResult.getFid(),
                        fileName, stream,
                        timeToLive, contentType));
    }

    /**
     * Save files by stream map.
     *
     * @param streamMap map of file name and file stream.
     * @return Files status.
     * @throws IOException
     */
    public LinkedHashMap<String, FileHandleStatus> saveFilesByStreamMap(LinkedHashMap<String, InputStream> streamMap)
            throws IOException {
        return saveFilesByStreamMap(streamMap, ContentType.DEFAULT_BINARY);
    }

    /**
     * Save files by stream map.
     *
     * @param streamMap   map of file name and file stream.
     * @param contentType file content type.
     * @return Files status.
     * @throws IOException
     */
    public LinkedHashMap<String, FileHandleStatus> saveFilesByStreamMap(LinkedHashMap<String, InputStream> streamMap,
                                                                        ContentType contentType) throws IOException {
        // Assign file key
        final AssignFileKeyParams params = new AssignFileKeyParams(
                assignFileKeyParams.getReplication(),
                streamMap.size(),
                assignFileKeyParams.getDataCenter(),
                assignFileKeyParams.getTtl(),
                assignFileKeyParams.getCollection()
        );

        final AssignFileKeyResult assignFileKeyResult =
                masterWrapper.assignFileKey(params);
        String uploadUrl;
        if (usingPublicUrl)
            uploadUrl = assignFileKeyResult.getPublicUrl();
        else
            uploadUrl = assignFileKeyResult.getUrl();

        // Upload file
        LinkedHashMap<String, FileHandleStatus> resultMap = new LinkedHashMap<String, FileHandleStatus>();
        int index = 0;
        for (String fileName : streamMap.keySet()) {
            if (index == 0)
                resultMap.put(fileName, new FileHandleStatus(assignFileKeyResult.getFid(),
                        volumeWrapper.uploadFile(
                                uploadUrl,
                                assignFileKeyResult.getFid(),
                                fileName,
                                streamMap.get(fileName),
                                timeToLive,
                                contentType)));
            else
                resultMap.put(fileName, new FileHandleStatus(assignFileKeyResult.getFid() + "_" + String.valueOf(index),
                        volumeWrapper.uploadFile(
                                uploadUrl,
                                assignFileKeyResult.getFid() + "_" + String.valueOf(index),
                                fileName,
                                streamMap.get(fileName),
                                timeToLive,
                                contentType)));
            index++;
        }
        return resultMap;
    }

    /**
     * Delete file.
     *
     * @param fileId file id whatever file is not exist.
     * @throws IOException
     */
    public void deleteFile(String fileId) throws IOException {
        final String targetUrl = getTargetUrl(fileId);

        if (!volumeWrapper.checkFileExist(targetUrl, fileId))
            throw new SeaweedfsFileNotFoundException("file is not exist");

        volumeWrapper.deleteFile(targetUrl, fileId);
    }

    /**
     * Delete files.
     *
     * @param fileIds file id list whatever file is not exist.
     * @throws IOException
     */
    public void deleteFiles(ArrayList<String> fileIds) throws IOException {
        LinkedHashMap<String, Boolean> resultMap = new LinkedHashMap<String, Boolean>();
        if (fileIds != null)
            for (String fileId : fileIds) {
                deleteFile(fileId);
            }
    }

    /**
     * Update file, if file id is not exist, it wouldn't throw any exception.
     *
     * @param fileId      file id whatever it is not exist.
     * @param fileName    file name.
     * @param stream      file stream.
     * @param contentType file content type.
     * @return Files status.
     * @throws IOException
     */
    public FileHandleStatus updateFileByStream(String fileId, String fileName, InputStream stream,
                                               ContentType contentType) throws IOException {
        final String targetUrl = getTargetUrl(fileId);

        if (!volumeWrapper.checkFileExist(targetUrl, fileId))
            throw new SeaweedfsFileNotFoundException("file is not exist");

        return new FileHandleStatus(fileId,
                volumeWrapper.uploadFile(targetUrl, fileId, fileName, stream, timeToLive, contentType));
    }

    /**
     * Update file, if file id is not exist, it wouldn't throw any exception.
     *
     * @param fileId   file id whatever it is not exist.
     * @param fileName file name.
     * @param stream   file stream.
     * @return Files status.
     * @throws IOException
     */
    public FileHandleStatus updateFileByStream(String fileId, String fileName,
                                               InputStream stream) throws IOException {
        return updateFileByStream(fileId, fileName, stream, ContentType.DEFAULT_BINARY);
    }

    /**
     * Get file stream, this is the faster method to get file stream from server.
     *
     * @param fileId file id.
     * @return File stream cache in jvm.
     */
    public StreamCache getFileStream(String fileId) throws IOException {
        final String targetUrl = getTargetUrl(fileId);
        return volumeWrapper.getFileStream(targetUrl, fileId);
    }

    /**
     * Get file status without file stream.
     *
     * @param fileId File id.
     * @return File status.
     * @throws IOException
     */
    public FileHandleStatus getFileStatus(String fileId) throws IOException {
        final String targetUrl = getTargetUrl(fileId);
        HeaderCache headerCache = volumeWrapper.getFileStatus(targetUrl, fileId);
        try {
            return new FileHandleStatus(fileId,
                    headerDateFormat.parse(headerCache.getLastHeader("Last-Modified").getValue()).getTime(),
                    headerCache.getLastHeader("Content-Disposition").getValue()
                            .substring(10, headerCache.getLastHeader("Content-Disposition").getValue().length() - 1),
                    headerCache.getLastHeader("Content-Type").getValue(),
                    Long.parseLong(headerCache.getLastHeader("Content-Length").getValue()));
        } catch (ParseException e) {
            throw new SeaweedfsException("Could not parse last modified time [" +
                    headerCache.getLastHeader("Last-Modified").getValue() + "] to long value");
        }
    }

    /**
     * Get file url, could get file directly from seaweedfs volume server.
     *
     * @param fileId File id.
     * @return File url.
     * @throws IOException
     */
    public String getFileUrl(String fileId) throws IOException {
        final String targetUrl = getTargetUrl(fileId);
        return targetUrl + "/" + fileId;
    }

    public int getSameRackCount() {
        return sameRackCount;
    }

    public void setSameRackCount(int sameRackCount) {
        if (sameRackCount < 0 || sameRackCount > 9)
            throw new IllegalArgumentException("seaweedfs replication at same rack count is error");
        this.sameRackCount = sameRackCount;
        buildReplicationFlag();
        buildAssignFileKeyParams();
    }

    public int getDiffRackCount() {
        return diffRackCount;
    }

    public void setDiffRackCount(int diffRackCount) {
        if (diffRackCount < 0 || diffRackCount > 9)
            throw new IllegalArgumentException("seaweedfs replication at diff rack count is error");
        this.diffRackCount = diffRackCount;
        buildReplicationFlag();
        buildAssignFileKeyParams();
    }

    public int getDiffDataCenterCount() {
        return diffDataCenterCount;
    }

    public void setDiffDataCenterCount(int diffDataCenterCount) {
        if (diffDataCenterCount < 0 || diffDataCenterCount > 9)
            throw new IllegalArgumentException("seaweedfs replication at diff data center count is error");
        this.diffDataCenterCount = diffDataCenterCount;
        buildReplicationFlag();
        buildAssignFileKeyParams();
    }

    public String getTimeToLive() {
        return timeToLive;
    }

    public void setTimeToLive(String timeToLive) {
        if (timeToLive != null && timeToLive.trim().isEmpty())
            this.timeToLive = null;
        else
            this.timeToLive = timeToLive;
        buildAssignFileKeyParams();
    }

    public String getDataCenter() {
        return dataCenter;
    }

    public void setDataCenter(String dataCenter) {
        this.dataCenter = dataCenter;
        buildAssignFileKeyParams();
    }

    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
        buildAssignFileKeyParams();
    }

    public boolean isUsingPublicUrl() {
        return usingPublicUrl;
    }

    public void setUsingPublicUrl(boolean usingPublicUrl) {
        this.usingPublicUrl = usingPublicUrl;
    }

    public boolean isLoadBalance() {
        return loadBalance;
    }

    public void setLoadBalance(boolean loadBalance) {
        this.loadBalance = loadBalance;
    }

    private void buildReplicationFlag() {
        this.replicationFlag = String.valueOf(diffDataCenterCount) +
                String.valueOf(diffRackCount) +
                String.valueOf(sameRackCount);
    }

    private void buildAssignFileKeyParams() {
        AssignFileKeyParams params = new AssignFileKeyParams();
        params.setCount(1);
        params.setTtl(timeToLive);
        params.setReplication(replicationFlag);
        params.setDataCenter(dataCenter);
        params.setCollection(collection);
        this.assignFileKeyParams = params;
    }

    private LocationResult getTargetLocation(String fileId) throws IOException {
        final LookupVolumeResult volumeResult = masterWrapper.lookupVolume(new LookupVolumeParams(fileId, collection));
        if (volumeResult.getLocations() == null || volumeResult.getLocations().size() == 0)
            throw new SeaweedfsFileDeleteException(fileId,
                    new SeaweedfsException("can not found the volume server"));
        if (loadBalance)
            return volumeResult.getRandomLocation();
        else
            return volumeResult.getLocations().get(0);
    }

    private String getTargetUrl(String fileId) throws IOException {
        if (usingPublicUrl)
            return getTargetLocation(fileId).getPublicUrl();
        else
            return getTargetLocation(fileId).getUrl();
    }

    @Override
    public void destroy() throws Exception {
        this.masterWrapper = null;
        this.volumeWrapper = null;
    }

    @Override
    public void afterPropertiesSet() throws Exception {

    }
}