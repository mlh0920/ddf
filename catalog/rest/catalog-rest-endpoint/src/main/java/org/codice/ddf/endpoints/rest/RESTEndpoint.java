/**
 * Copyright (c) Codice Foundation
 * <p/>
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package org.codice.ddf.endpoints.rest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.Encoded;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.codec.CharEncoding;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.MultipartBody;
import org.codice.ddf.platform.util.TemporaryFileBackedOutputStream;
import org.opengis.filter.Filter;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.ByteSource;
import com.google.common.io.FileBackedOutputStream;

import ddf.catalog.CatalogFramework;
import ddf.catalog.Constants;
import ddf.catalog.content.data.impl.ContentItemImpl;
import ddf.catalog.content.operation.CreateStorageRequest;
import ddf.catalog.content.operation.UpdateStorageRequest;
import ddf.catalog.content.operation.impl.CreateStorageRequestImpl;
import ddf.catalog.content.operation.impl.UpdateStorageRequestImpl;
import ddf.catalog.data.Attribute;
import ddf.catalog.data.AttributeDescriptor;
import ddf.catalog.data.AttributeType;
import ddf.catalog.data.BinaryContent;
import ddf.catalog.data.ContentType;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.MetacardCreationException;
import ddf.catalog.data.MetacardType;
import ddf.catalog.data.Result;
import ddf.catalog.data.impl.AttributeImpl;
import ddf.catalog.data.impl.BinaryContentImpl;
import ddf.catalog.data.impl.MetacardImpl;
import ddf.catalog.federation.FederationException;
import ddf.catalog.filter.FilterBuilder;
import ddf.catalog.operation.CreateRequest;
import ddf.catalog.operation.CreateResponse;
import ddf.catalog.operation.QueryResponse;
import ddf.catalog.operation.SourceInfoResponse;
import ddf.catalog.operation.UpdateRequest;
import ddf.catalog.operation.impl.CreateRequestImpl;
import ddf.catalog.operation.impl.DeleteRequestImpl;
import ddf.catalog.operation.impl.QueryImpl;
import ddf.catalog.operation.impl.QueryRequestImpl;
import ddf.catalog.operation.impl.SourceInfoRequestEnterprise;
import ddf.catalog.operation.impl.UpdateRequestImpl;
import ddf.catalog.resource.DataUsageLimitExceededException;
import ddf.catalog.resource.Resource;
import ddf.catalog.source.IngestException;
import ddf.catalog.source.InternalIngestException;
import ddf.catalog.source.SourceDescriptor;
import ddf.catalog.source.SourceUnavailableException;
import ddf.catalog.source.UnsupportedQueryException;
import ddf.catalog.transform.CatalogTransformerException;
import ddf.catalog.transform.InputTransformer;
import ddf.mime.MimeTypeMapper;
import ddf.mime.MimeTypeResolutionException;
import ddf.mime.MimeTypeResolver;
import ddf.mime.MimeTypeToTransformerMapper;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;

@Path("/")
public class RESTEndpoint implements RESTService {

    static final String DEFAULT_METACARD_TRANSFORMER = "xml";

    private static final String DEFAULT_FILE_EXTENSION = "bin";

    private static final String BYTES_TO_SKIP = "BytesToSkip";

    private static final Logger LOGGER = LoggerFactory.getLogger(RESTEndpoint.class);

    private static final Logger INGEST_LOGGER =
            LoggerFactory.getLogger(Constants.INGEST_LOGGER_NAME);

    private static final String HEADER_RANGE = "Range";

    private static final String HEADER_ACCEPT_RANGES = "Accept-Ranges";

    private static final String HEADER_CONTENT_LENGTH = "Content-Length";

    private static final String HEADER_CONTENT_DISPOSITION = "Content-Disposition";

    private static final String FILE_ATTACHMENT_CONTENT_ID = "file";

    private static final String FILENAME_CONTENT_DISPOSITION_PARAMETER_NAME = "filename";

    private static final String BYTES = "bytes";

    private static final String BYTES_EQUAL = "bytes=";

    private static final String JSON_MIME_TYPE_STRING = "application/json";

    private static final String DEFAULT_MIME_TYPE = "application/octet-stream";

    private static final String DEFAULT_FILE_NAME = "file";

    /**
     * Basic mime types that will be attempted to refine to a more accurate mime type
     * based on the file extension of the filename specified in the create request.
     */
    private static final List<String> REFINEABLE_MIME_TYPES = Arrays.asList(DEFAULT_MIME_TYPE,
            "text/plain");

    private static MimeType jsonMimeType = null;

    static {
        MimeType mime = null;
        try {
            mime = new MimeType(JSON_MIME_TYPE_STRING);
        } catch (MimeTypeParseException e) {
            LOGGER.info("Failed to create json mimetype.");
        }
        jsonMimeType = mime;

    }

    private List<MetacardType> metacardTypes;

    private MimeTypeMapper mimeTypeMapper;

    private FilterBuilder filterBuilder;

    private CatalogFramework catalogFramework;

    private MimeTypeToTransformerMapper mimeTypeToTransformerMapper;

    private MimeTypeResolver tikaMimeTypeResolver;

    public RESTEndpoint(CatalogFramework framework) {
        LOGGER.trace("Constructing REST Endpoint");
        this.catalogFramework = framework;
        LOGGER.trace(("Rest Endpoint constructed successfully"));
    }

    BundleContext getBundleContext() {
        Bundle bundle = FrameworkUtil.getBundle(RESTEndpoint.class);
        return bundle.getBundleContext();
    }

    /**
     * REST Head. Retrieves information regarding the entry specified by the id.
     * This can be used to verify that the Range header is supported (the Accept-Ranges header is returned) and to
     * get the size of the requested resource for use in Content-Range requests.
     *
     * @param id
     * @param uriInfo
     * @param httpRequest
     * @return
     */
    @HEAD
    @Path("/{id}")
    public Response getHeaders(@PathParam("id") String id, @Context UriInfo uriInfo,
            @Context HttpServletRequest httpRequest) {

        return getHeaders(null, id, uriInfo, httpRequest);
    }

    /**
     * REST Head. Returns headers only.  Primarily used to let the client know that range requests (though limited)
     * are accepted.
     *
     * @param sourceid
     * @param id
     * @param uriInfo
     * @param httpRequest
     * @return
     */
    @HEAD
    @Path("/sources/{sourceid}/{id}")
    public Response getHeaders(@PathParam("sourceid") String sourceid, @PathParam("id") String id,
            @Context UriInfo uriInfo, @Context HttpServletRequest httpRequest) {

        Response response;
        Response.ResponseBuilder responseBuilder;
        QueryResponse queryResponse;
        Metacard card = null;

        LOGGER.trace("getHeaders");
        URI absolutePath = uriInfo.getAbsolutePath();
        MultivaluedMap<String, String> map = uriInfo.getQueryParameters();

        if (id != null) {
            LOGGER.debug("Got id: {}", id);
            LOGGER.debug("Map of query parameters: \n{}", map.toString());

            Map<String, Serializable> convertedMap = convert(map);
            convertedMap.put("url", absolutePath.toString());

            LOGGER.debug("Map converted, retrieving product.");

            // default to xml if no transformer specified
            try {
                String transformer = DEFAULT_METACARD_TRANSFORMER;

                Filter filter = getFilterBuilder().attribute(Metacard.ID)
                        .is()
                        .equalTo()
                        .text(id);

                Collection<String> sources = null;
                if (sourceid != null) {
                    sources = new ArrayList<String>();
                    sources.add(sourceid);
                }

                QueryRequestImpl request = new QueryRequestImpl(new QueryImpl(filter), sources);
                request.setProperties(convertedMap);
                queryResponse = catalogFramework.query(request, null);

                // pull the metacard out of the blocking queue
                List<Result> results = queryResponse.getResults();

                // TODO: should be poll? do we want to specify a timeout? (will
                // return null if timeout elapsed)
                if (results != null && !results.isEmpty()) {
                    card = results.get(0)
                            .getMetacard();
                }

                if (card == null) {
                    throw new ServerErrorException("Unable to retrieve requested metacard.",
                            Status.NOT_FOUND);
                }

                LOGGER.debug("Calling transform.");
                final BinaryContent content = catalogFramework.transform(card,
                        transformer,
                        convertedMap);
                LOGGER.debug("Read and transform complete, preparing response.");

                responseBuilder = Response.noContent();

                // Add the Accept-ranges header to let the client know that we accept ranges in bytes
                responseBuilder.header(HEADER_ACCEPT_RANGES, BYTES);

                String filename = null;

                if (content instanceof Resource) {
                    // If we got a resource, we can extract the filename.
                    filename = ((Resource) content).getName();
                } else {
                    String fileExtension = getFileExtensionForMimeType(content.getMimeTypeValue());
                    if (StringUtils.isNotBlank(fileExtension)) {
                        filename = id + fileExtension;
                    }
                }

                if (StringUtils.isNotBlank(filename)) {
                    LOGGER.debug("filename: {}", filename);
                    responseBuilder.header(HEADER_CONTENT_DISPOSITION,
                            "inline; filename=\"" + filename + "\"");
                }

                long size = content.getSize();
                if (size > 0) {
                    responseBuilder.header(HEADER_CONTENT_LENGTH, size);
                }

                response = responseBuilder.build();

            } catch (FederationException e) {
                String exceptionMessage = "READ failed due to unexpected exception: ";
                LOGGER.info(exceptionMessage, e);
                throw new ServerErrorException(exceptionMessage, Status.INTERNAL_SERVER_ERROR);
            } catch (CatalogTransformerException e) {
                String exceptionMessage =
                        "Unable to transform Metacard.  Try different transformer: ";
                LOGGER.info(exceptionMessage, e);
                throw new ServerErrorException(exceptionMessage, Status.INTERNAL_SERVER_ERROR);
            } catch (SourceUnavailableException e) {
                String exceptionMessage =
                        "Cannot obtain query results because source is unavailable: ";
                LOGGER.info(exceptionMessage, e);
                throw new ServerErrorException(exceptionMessage, Status.INTERNAL_SERVER_ERROR);
            } catch (UnsupportedQueryException e) {
                String exceptionMessage =
                        "Specified query is unsupported.  Change query and resubmit: ";
                LOGGER.info(exceptionMessage, e);
                throw new ServerErrorException(exceptionMessage, Status.BAD_REQUEST);

                // The catalog framework will throw this if any of the transformers blow up. We need to
                // catch this exception
                // here or else execution will return to CXF and we'll lose this message and end up with
                // a huge stack trace
                // in a GUI or whatever else is connected to this endpoint
            } catch (IllegalArgumentException e) {
                throw new ServerErrorException(e, Status.BAD_REQUEST);
            }
        } else {
            throw new ServerErrorException("No ID specified.", Status.BAD_REQUEST);
        }
        return response;
    }

    /**
     * REST Get. Retrieves the metadata entry specified by the id. Transformer argument is optional,
     * but is used to specify what format the data should be returned.
     *
     * @param id
     * @param transformerParam (OPTIONAL)
     * @param uriInfo
     * @return
     * @throws ServerErrorException
     */
    @GET
    @Path("/{id}")
    public Response getDocument(@PathParam("id") String id,
            @QueryParam("transform") String transformerParam, @Context UriInfo uriInfo,
            @Context HttpServletRequest httpRequest) {

        return getDocument(null, id, transformerParam, uriInfo, httpRequest);
    }

    /**
     * REST Get. Retrieves information regarding sources available.
     *
     * @param uriInfo
     * @param httpRequest
     * @return
     */
    @GET
    @Path(SOURCES_PATH)
    public Response getDocument(@Context UriInfo uriInfo, @Context HttpServletRequest httpRequest) {
        BinaryContent content;
        ResponseBuilder responseBuilder;
        String sourcesString = null;

        JSONArray resultsList = new JSONArray();
        SourceInfoResponse sources;
        try {
            SourceInfoRequestEnterprise sourceInfoRequestEnterprise =
                    new SourceInfoRequestEnterprise(true);

            sources = catalogFramework.getSourceInfo(sourceInfoRequestEnterprise);
            for (SourceDescriptor source : sources.getSourceInfo()) {
                JSONObject sourceObj = new JSONObject();
                sourceObj.put("id", source.getSourceId());
                sourceObj.put("version", source.getVersion() != null ? source.getVersion() : "");
                sourceObj.put("available", Boolean.valueOf(source.isAvailable()));
                JSONArray contentTypesObj = new JSONArray();
                if (source.getContentTypes() != null) {
                    for (ContentType contentType : source.getContentTypes()) {
                        if (contentType != null && contentType.getName() != null) {
                            JSONObject contentTypeObj = new JSONObject();
                            contentTypeObj.put("name", contentType.getName());
                            contentTypeObj.put("version",
                                    contentType.getVersion() != null ?
                                            contentType.getVersion() :
                                            "");
                            contentTypesObj.add(contentTypeObj);
                        }
                    }
                }
                sourceObj.put("contentTypes", contentTypesObj);
                resultsList.add(sourceObj);
            }
        } catch (SourceUnavailableException e) {
            LOGGER.info("Unable to retrieve Sources. {}", e.getMessage());
            LOGGER.debug("Unable to retrieve Sources", e);
        }

        sourcesString = JSONValue.toJSONString(resultsList);
        content = new BinaryContentImpl(new ByteArrayInputStream(sourcesString.getBytes(
                StandardCharsets.UTF_8)), jsonMimeType);
        responseBuilder = Response.ok(content.getInputStream(), content.getMimeTypeValue());

        // Add the Accept-ranges header to let the client know that we accept ranges in bytes
        responseBuilder.header(HEADER_ACCEPT_RANGES, BYTES);

        return responseBuilder.build();
    }

    /**
     * REST Get. Retrieves the metadata entry specified by the id from the federated source
     * specified by sourceid. Transformer argument is optional, but is used to specify what format
     * the data should be returned.
     *
     * @param encodedSourceId
     * @param encodedId
     * @param transformerParam
     * @param uriInfo
     * @return
     */
    @GET
    @Path("/sources/{sourceid}/{id}")
    public Response getDocument(@Encoded @PathParam("sourceid") String encodedSourceId,
            @Encoded @PathParam("id") String encodedId,
            @QueryParam("transform") String transformerParam, @Context UriInfo uriInfo,
            @Context HttpServletRequest httpRequest) {

        Response response = null;
        Response.ResponseBuilder responseBuilder;
        QueryResponse queryResponse;
        Metacard card = null;

        LOGGER.trace("GET");
        URI absolutePath = uriInfo.getAbsolutePath();
        MultivaluedMap<String, String> map = uriInfo.getQueryParameters();

        if (encodedId != null) {
            LOGGER.debug("Got id: {}", encodedId);
            LOGGER.debug("Got service: {}", transformerParam);
            LOGGER.debug("Map of query parameters: \n{}", map.toString());

            Map<String, Serializable> convertedMap = convert(map);
            convertedMap.put("url", absolutePath.toString());

            LOGGER.debug("Map converted, retrieving product.");

            // default to xml if no transformer specified
            try {
                String id = URLDecoder.decode(encodedId, CharEncoding.UTF_8);

                String transformer = DEFAULT_METACARD_TRANSFORMER;
                if (transformerParam != null) {
                    transformer = transformerParam;
                }
                Filter filter = getFilterBuilder().attribute(Metacard.ID)
                        .is()
                        .equalTo()
                        .text(id);

                Collection<String> sources = null;
                if (encodedSourceId != null) {
                    String sourceid = URLDecoder.decode(encodedSourceId, CharEncoding.UTF_8);
                    sources = new ArrayList<String>();
                    sources.add(sourceid);
                }

                QueryRequestImpl request = new QueryRequestImpl(new QueryImpl(filter), sources);
                request.setProperties(convertedMap);
                queryResponse = catalogFramework.query(request, null);

                // pull the metacard out of the blocking queue
                List<Result> results = queryResponse.getResults();

                // TODO: should be poll? do we want to specify a timeout? (will
                // return null if timeout elapsed)
                if (results != null && !results.isEmpty()) {
                    card = results.get(0)
                            .getMetacard();
                }

                if (card == null) {
                    throw new ServerErrorException("Unable to retrieve requested metacard.",
                            Status.NOT_FOUND);
                }

                // Check for Range header set the value in the map appropriately so that the catalogFramework
                // can take care of the skipping
                long bytesToSkip = getRangeStart(httpRequest);

                if (bytesToSkip > 0) {
                    LOGGER.debug("Bytes to skip: {}", String.valueOf(bytesToSkip));
                    convertedMap.put(BYTES_TO_SKIP, bytesToSkip);
                }

                LOGGER.debug("Calling transform.");
                final BinaryContent content = catalogFramework.transform(card,
                        transformer,
                        convertedMap);
                LOGGER.debug("Read and transform complete, preparing response.");

                responseBuilder = Response.ok(content.getInputStream(), content.getMimeTypeValue());

                // Add the Accept-ranges header to let the client know that we accept ranges in bytes
                responseBuilder.header(HEADER_ACCEPT_RANGES, BYTES);

                String filename = null;

                if (content instanceof Resource) {
                    // If we got a resource, we can extract the filename.
                    filename = ((Resource) content).getName();
                } else {
                    String fileExtension = getFileExtensionForMimeType(content.getMimeTypeValue());
                    if (StringUtils.isNotBlank(fileExtension)) {
                        filename = id + fileExtension;
                    }
                }

                if (StringUtils.isNotBlank(filename)) {
                    LOGGER.debug("filename: {}", filename);
                    responseBuilder.header(HEADER_CONTENT_DISPOSITION,
                            "inline; filename=\"" + filename + "\"");
                }

                long size = content.getSize();
                if (size > 0) {
                    responseBuilder.header(HEADER_CONTENT_LENGTH, size);
                }

                response = responseBuilder.build();
            } catch (FederationException e) {
                String exceptionMessage = "READ failed due to unexpected exception: ";
                LOGGER.info(exceptionMessage, e);
                throw new ServerErrorException(exceptionMessage, Status.INTERNAL_SERVER_ERROR);
            } catch (CatalogTransformerException e) {
                String exceptionMessage =
                        "Unable to transform Metacard.  Try different transformer: ";
                LOGGER.info(exceptionMessage, e);
                throw new ServerErrorException(exceptionMessage, Status.INTERNAL_SERVER_ERROR);
            } catch (SourceUnavailableException e) {
                String exceptionMessage =
                        "Cannot obtain query results because source is unavailable: ";
                LOGGER.info(exceptionMessage, e);
                throw new ServerErrorException(exceptionMessage, Status.INTERNAL_SERVER_ERROR);
            } catch (UnsupportedQueryException e) {
                String exceptionMessage =
                        "Specified query is unsupported.  Change query and resubmit: ";
                LOGGER.info(exceptionMessage, e);
                throw new ServerErrorException(exceptionMessage, Status.BAD_REQUEST);
            } catch (DataUsageLimitExceededException e) {
                String exceptionMessage = "Unable to process request. Data usage limit exceeded: ";
                LOGGER.debug(exceptionMessage, e);
                throw new ServerErrorException(exceptionMessage, Status.REQUEST_ENTITY_TOO_LARGE);
                // The catalog framework will throw this if any of the transformers blow up.
                // We need to catch this exception here or else execution will return to CXF and
                // we'll lose this message and end up with a huge stack trace in a GUI or whatever
                // else is connected to this endpoint
            } catch (RuntimeException | UnsupportedEncodingException e) {
                String exceptionMessage = "Unknown error occurred while processing request: ";
                LOGGER.info(exceptionMessage, e);
                throw new ServerErrorException(exceptionMessage, Status.INTERNAL_SERVER_ERROR);
            }
        } else {
            throw new ServerErrorException("No ID specified.", Status.BAD_REQUEST);
        }
        return response;
    }

    @POST
    @Path("/metacard")
    public Response createMetacard(MultipartBody multipartBody, @Context UriInfo requestUriInfo,
            @QueryParam("transform") String transformerParam) {

        LOGGER.trace("ENTERING: createMetacard");

        String contentUri = multipartBody.getAttachmentObject("contentUri", String.class);
        LOGGER.debug("contentUri = {}", contentUri);

        InputStream stream = null;
        String filename = null;
        String contentType = null;
        Response response = null;

        String transformer = DEFAULT_METACARD_TRANSFORMER;
        if (transformerParam != null) {
            transformer = transformerParam;
        }

        Attachment contentPart = multipartBody.getAttachment(FILE_ATTACHMENT_CONTENT_ID);
        if (contentPart != null) {
            // Example Content-Type header:
            // Content-Type: application/json;id=geojson
            if (contentPart.getContentType() != null) {
                contentType = contentPart.getContentType()
                        .toString();
            }

            // Get the file contents as an InputStream and ensure the stream is positioned
            // at the beginning
            try {
                stream = contentPart.getDataHandler()
                        .getInputStream();
                if (stream != null && stream.available() == 0) {
                    stream.reset();
                }
            } catch (IOException e) {
                LOGGER.info("IOException reading stream from file attachment in multipart body", e);
            }
        } else {
            LOGGER.debug("No file contents attachment found");
        }

        MimeType mimeType = null;
        if (contentType != null) {
            try {
                mimeType = new MimeType(contentType);
            } catch (MimeTypeParseException e) {
                LOGGER.debug("Unable to create MimeType from raw data {}", contentType);
            }
        } else {
            LOGGER.debug("No content type specified in request");
        }

        try {
            Metacard metacard = generateMetacard(mimeType, "assigned-when-ingested", stream, null);
            String metacardId = metacard.getId();
            LOGGER.debug("Metacard {} created", metacardId);
            LOGGER.debug("Transforming metacard {} to {} to be able to return it to client",
                    metacardId,
                    transformer);
            final BinaryContent content = catalogFramework.transform(metacard, transformer, null);
            LOGGER.debug("Metacard to {} transform complete for {}, preparing response.",
                    transformer,
                    metacardId);

            Response.ResponseBuilder responseBuilder = Response.ok(content.getInputStream(),
                    content.getMimeTypeValue());
            response = responseBuilder.build();
        } catch (MetacardCreationException | CatalogTransformerException e) {
            throw new ServerErrorException("Unable to create metacard", Status.BAD_REQUEST);
        }

        LOGGER.trace("EXITING: createMetacard");

        return response;
    }

    /**
     * REST Put. Updates the specified entry with the provided document.
     *
     * @param id
     * @param message
     * @return
     */
    @PUT
    @Path("/{id}")
    @Consumes({"text/*", "application/*"})
    public Response updateDocument(@PathParam("id") String id, @Context HttpHeaders headers,
            @Context HttpServletRequest httpRequest,
            @QueryParam("transform") String transformerParam, InputStream message) {
        return updateDocument(id, headers, httpRequest, null, transformerParam, message);
    }

    /**
     * REST Put. Updates the specified entry with the provided document.
     *
     * @param id
     * @param message
     * @return
     */
    @PUT
    @Path("/{id}")
    @Consumes("multipart/*")
    public Response updateDocument(@PathParam("id") String id, @Context HttpHeaders headers,
            @Context HttpServletRequest httpRequest, MultipartBody multipartBody,
            @QueryParam("transform") String transformerParam, InputStream message) {
        LOGGER.trace("PUT");
        Response response;

        try {
            if (id != null && message != null) {

                MimeType mimeType = getMimeType(headers);

                CreateInfo createInfo = null;
                if (multipartBody != null) {
                    List<Attachment> contentParts = multipartBody.getAllAttachments();
                    if (contentParts != null && contentParts.size() > 0) {
                        createInfo = parseAttachment(contentParts.get(0));
                    } else {
                        LOGGER.debug("No file contents attachment found");
                    }
                }

                if (createInfo == null) {
                    UpdateRequest updateRequest = new UpdateRequestImpl(id,
                            generateMetacard(mimeType, id, message, transformerParam));
                    catalogFramework.update(updateRequest);
                } else {
                    UpdateStorageRequest streamUpdateRequest = new UpdateStorageRequestImpl(
                            Collections.singletonList(
                                    new IncomingContentItem(id, createInfo.getStream(),
                                            createInfo.getContentType(), createInfo.getFilename(),
                                            0, createInfo.getMetacard())), null);
                    catalogFramework.update(streamUpdateRequest);
                }

                LOGGER.debug("Metacard {} updated.", id);
                response = Response.ok()
                        .build();
            } else {
                String errorResponseString = "Both ID and content are needed to perform UPDATE.";
                LOGGER.info(errorResponseString);
                throw new ServerErrorException(errorResponseString, Status.BAD_REQUEST);
            }
        } catch (SourceUnavailableException e) {
            String exceptionMessage = "Cannot update catalog entry: Source is unavailable: ";
            LOGGER.info(exceptionMessage, e);
            throw new ServerErrorException(exceptionMessage, Status.INTERNAL_SERVER_ERROR);
        } catch (InternalIngestException e) {
            String exceptionMessage = "Error cataloging updated metadata: ";
            LOGGER.info(exceptionMessage, e);
            throw new ServerErrorException(exceptionMessage, Status.INTERNAL_SERVER_ERROR);
        } catch (MetacardCreationException | IngestException e) {
            String exceptionMessage = "Error cataloging updated metadata: ";
            LOGGER.info(exceptionMessage, e);
            throw new ServerErrorException(exceptionMessage, Status.BAD_REQUEST);
        }
        return response;
    }

    @POST
    @Consumes({"text/*", "application/*"})
    public Response addDocument(@Context HttpHeaders headers, @Context UriInfo requestUriInfo,
            @Context HttpServletRequest httpRequest,
            @QueryParam("transform") String transformerParam, InputStream message) {
        return addDocument(headers, requestUriInfo, httpRequest, null, transformerParam, message);
    }

    /**
     * REST Post. Creates a new metadata entry in the catalog.
     *
     * @param message
     * @return
     */
    @POST
    @Consumes("multipart/*")
    public Response addDocument(@Context HttpHeaders headers, @Context UriInfo requestUriInfo,
            @Context HttpServletRequest httpRequest, MultipartBody multipartBody,
            @QueryParam("transform") String transformerParam, InputStream message) {
        LOGGER.debug("POST");
        Response response;

        MimeType mimeType = getMimeType(headers);

        try {
            if (message != null) {
                CreateInfo createInfo = null;
                if (multipartBody != null) {
                    List<Attachment> contentParts = multipartBody.getAllAttachments();
                    if (contentParts != null && contentParts.size() > 0) {
                        createInfo = parseAttachments(contentParts, transformerParam);
                    } else {
                        LOGGER.debug("No file contents attachment found");
                    }
                }

                CreateResponse createResponse;
                if (createInfo == null) {
                    CreateRequest createRequest = new CreateRequestImpl(
                            generateMetacard(mimeType, null, message, transformerParam));
                    createResponse = catalogFramework.create(createRequest);
                } else {
                    CreateStorageRequest streamCreateRequest = new CreateStorageRequestImpl(
                            Collections.singletonList(
                                    new IncomingContentItem(createInfo.getStream(),
                                            createInfo.getContentType(), createInfo.getFilename(),
                                            createInfo.getMetacard())), null);
                    createResponse = catalogFramework.create(streamCreateRequest);
                }

                String id = createResponse.getCreatedMetacards()
                        .get(0)
                        .getId();

                LOGGER.debug("Create Response id [{}]", id);

                UriBuilder uriBuilder = requestUriInfo.getAbsolutePathBuilder()
                        .path("/" + id);

                ResponseBuilder responseBuilder = Response.created(uriBuilder.build());

                responseBuilder.header(Metacard.ID, id);

                response = responseBuilder.build();

                LOGGER.debug("Entry successfully saved, id: {}", id);
                if (INGEST_LOGGER.isInfoEnabled()) {
                    INGEST_LOGGER.info("Entry successfully saved, id: {}", id);
                }
            } else {
                String errorMessage = "No content found, cannot do CREATE.";
                LOGGER.info(errorMessage);
                throw new ServerErrorException(errorMessage, Status.BAD_REQUEST);
            }
        } catch (SourceUnavailableException e) {
            String exceptionMessage =
                    "Cannot create catalog entry because source is unavailable: ";
            LOGGER.info(exceptionMessage, e);
            // Catalog framework logs these exceptions to the ingest logger so we don't have to.
            throw new ServerErrorException(exceptionMessage, Status.INTERNAL_SERVER_ERROR);
        } catch (InternalIngestException e) {
            String exceptionMessage = "Error while storing entry in catalog: ";
            LOGGER.info(exceptionMessage, e);
            // Catalog framework logs these exceptions to the ingest logger so we don't have to.
            throw new ServerErrorException(exceptionMessage, Status.INTERNAL_SERVER_ERROR);
        } catch (MetacardCreationException | IngestException e) {
            String exceptionMessage = "Error while storing entry in catalog: ";
            LOGGER.info(exceptionMessage, e);
            // Catalog framework logs these exceptions to the ingest logger so we don't have to.
            throw new ServerErrorException(exceptionMessage, Status.BAD_REQUEST);
        } finally {
            IOUtils.closeQuietly(message);
        }

        return response;
    }

    CreateInfo parseAttachments(List<Attachment> contentParts, String transformerParam) {

        if (contentParts.size() == 1) {
            Attachment contentPart = contentParts.get(0);
            return parseAttachment(contentPart);
        }
        List<Attribute> attributes = new ArrayList<>(contentParts.size());
        Metacard metacard = null;
        CreateInfo createInfo = null;

        for (Attachment attachment : contentParts) {
            String name = attachment.getContentDisposition()
                    .getParameter("name");
            String parsedName = (name.startsWith("parse.")) ? name.substring(6) : name;
            try {
                InputStream inputStream = attachment.getDataHandler()
                        .getInputStream();
                if (name.equals("parse.resource")) {
                    createInfo = parseAttachment(attachment);
                } else if (name.equals("parse.metadata")) {
                    metacard = parseMetadata(transformerParam, metacard, attachment, inputStream);
                } else {
                    parseOverrideAttributes(attributes, parsedName, inputStream);
                }
            } catch (IOException e) {
                LOGGER.debug(
                        "Unable to get input stream for mime attachment. Ignoring override attribute: {}",
                        name, e);
            }

        }
        if (createInfo == null) {
            throw new IllegalArgumentException("No parse.resource specified in request.");
        }
        if (metacard == null) {
            metacard = new MetacardImpl();
        }
        for (Attribute attribute : attributes) {
            metacard.setAttribute(attribute);
        }
        createInfo.setMetacard(metacard);

        return createInfo;
    }

    private void parseOverrideAttributes(List<Attribute> attributes, String parsedName,
            InputStream inputStream) {
        Optional<AttributeType.AttributeFormat> attributeFormat = metacardTypes.stream()
                .map(metacardType -> metacardType.getAttributeDescriptor(parsedName))
                .filter(Objects::nonNull)
                .findFirst()
                .map(AttributeDescriptor::getType)
                .map(AttributeType::getAttributeFormat);
        attributeFormat.ifPresent(attributeFormat1 -> {
            try {
                switch (attributeFormat1) {
                case XML:
                case GEOMETRY:
                case STRING:
                    attributes.add(new AttributeImpl(parsedName,
                            IOUtils.toString(inputStream)));
                    break;
                case BOOLEAN:
                    attributes.add(new AttributeImpl(parsedName,
                            Boolean.valueOf(IOUtils.toString(inputStream))));
                    break;
                case SHORT:
                    attributes.add(new AttributeImpl(parsedName,
                            Short.valueOf(IOUtils.toString(inputStream))));
                    break;
                case LONG:
                    attributes.add(new AttributeImpl(parsedName,
                            Long.valueOf(IOUtils.toString(inputStream))));
                    break;
                case INTEGER:
                    attributes.add(new AttributeImpl(parsedName,
                            Integer.valueOf(IOUtils.toString(inputStream))));
                    break;
                case FLOAT:
                    attributes.add(new AttributeImpl(parsedName,
                            Float.valueOf(IOUtils.toString(inputStream))));
                    break;
                case DOUBLE:
                    attributes.add(new AttributeImpl(parsedName,
                            Double.valueOf(IOUtils.toString(inputStream))));
                    break;
                case DATE:
                    attributes.add(new AttributeImpl(parsedName, Date.from(
                            Instant.parse(IOUtils.toString(inputStream)))));
                    break;
                case BINARY:
                    attributes.add(new AttributeImpl(parsedName,
                            IOUtils.toByteArray(inputStream)));
                    break;
                case OBJECT:
                    LOGGER.debug("Object type not supported for override");
                    break;
                }
            } catch (IOException e) {
                LOGGER.debug("Unable to read attribute to override", e);
            } finally {
                IOUtils.closeQuietly(inputStream);
            }

        });
    }

    private Metacard parseMetadata(String transformerParam, Metacard metacard,
            Attachment attachment, InputStream inputStream) {
        String transformer = DEFAULT_METACARD_TRANSFORMER;
        if (transformerParam != null) {
            transformer = transformerParam;
        }
        try {
            MimeType mimeType = new MimeType(attachment.getContentType()
                    .toString());
            metacard = generateMetacard(mimeType,
                    "assigned-when-ingested", inputStream, transformer);
        } catch (MimeTypeParseException | MetacardCreationException e) {
            LOGGER.debug("Unable to parse metadata {}",
                    attachment.getContentType()
                            .toString());
        } finally {
            IOUtils.closeQuietly(inputStream);
        }
        return metacard;
    }

    CreateInfo parseAttachment(Attachment contentPart) {
        CreateInfo createInfo = new CreateInfo();
        InputStream stream = null;
        FileBackedOutputStream fbos = null;
        String filename = null;
        String contentType = null;

        // Get the file contents as an InputStream and ensure the stream is positioned
        // at the beginning
        try {
            stream = contentPart.getDataHandler()
                    .getInputStream();
            if (stream != null && stream.available() == 0) {
                stream.reset();
            }
            createInfo.setStream(stream);
        } catch (IOException e) {
            LOGGER.info("IOException reading stream from file attachment in multipart body", e);
        }

        // Example Content-Type header:
        // Content-Type: application/json;id=geojson
        if (contentPart.getContentType() != null) {
            contentType = contentPart.getContentType()
                    .toString();
        }

        if (contentPart.getContentDisposition() != null) {
            filename = contentPart.getContentDisposition()
                    .getParameter(FILENAME_CONTENT_DISPOSITION_PARAMETER_NAME);
        }

        // Only interested in attachments for file uploads. Any others should be covered by
        // the FormParam arguments.
        // If the filename was not specified, then generate a default filename based on the
        // specified content type.
        if (StringUtils.isEmpty(filename)) {
            LOGGER.debug("No filename parameter provided - generating default filename");
            String fileExtension = DEFAULT_FILE_EXTENSION;
            try {
                fileExtension = mimeTypeMapper.getFileExtensionForMimeType(contentType); // DDF-2307
                if (StringUtils.isEmpty(fileExtension)) {
                    fileExtension = DEFAULT_FILE_EXTENSION;
                }
            } catch (MimeTypeResolutionException e) {
                LOGGER.debug("Exception getting file extension for contentType = {}",
                        contentType);
            }
            filename = DEFAULT_FILE_NAME + "." + fileExtension; // DDF-2263
            LOGGER.debug("No filename parameter provided - default to {}", filename);
        } else {
            filename = FilenameUtils.getName(filename);

            // DDF-908: filename with extension was specified by the client. If the
            // contentType is null or the browser default, try to refine the contentType
            // by determining the mime type based on the filename's extension.
            if (StringUtils.isEmpty(contentType) || REFINEABLE_MIME_TYPES.contains(contentType)) {
                String fileExtension = FilenameUtils.getExtension(filename);
                LOGGER.debug("fileExtension = {}, contentType before refinement = {}",
                        fileExtension, contentType);
                try {
                    contentType = mimeTypeMapper.getMimeTypeForFileExtension(fileExtension);
                } catch (MimeTypeResolutionException e) {
                    LOGGER.debug(
                            "Unable to refine contentType {} based on filename extension {}",
                            contentType, fileExtension);
                }
                LOGGER.debug("Refined contentType = {}", contentType);
            }
        }

        createInfo.setContentType(contentType);
        createInfo.setFilename(filename);

        return createInfo;
    }

    /**
     * REST Delete. Deletes a record from the catalog.
     *
     * @param id
     * @return
     */
    @DELETE
    @Path("/{id}")
    public Response deleteDocument(@PathParam("id") String id,
            @Context HttpServletRequest httpRequest) {
        LOGGER.debug("DELETE");
        Response response;
        try {
            if (id != null) {
                DeleteRequestImpl deleteReq = new DeleteRequestImpl(id);

                catalogFramework.delete(deleteReq);
                response = Response.ok(id)
                        .build();
                LOGGER.debug("Attempting to delete Metacard with id: {}", id);
            } else {
                String errorMessage = "ID of entry not specified, cannot do DELETE.";
                LOGGER.info(errorMessage);
                throw new ServerErrorException(errorMessage, Status.BAD_REQUEST);
            }
        } catch (SourceUnavailableException ce) {
            String exceptionMessage =
                    "Could not delete entry from catalog since the source is unavailable: ";
            LOGGER.info(exceptionMessage, ce);
            throw new ServerErrorException(exceptionMessage, Status.INTERNAL_SERVER_ERROR);
        } catch (InternalIngestException e) {
            String exceptionMessage = "Error deleting entry from catalog: ";
            LOGGER.info(exceptionMessage, e);
            throw new ServerErrorException(exceptionMessage, Status.INTERNAL_SERVER_ERROR);
        } catch (IngestException e) {
            String exceptionMessage = "Error deleting entry from catalog: ";
            LOGGER.info(exceptionMessage, e);
            throw new ServerErrorException(exceptionMessage, Status.BAD_REQUEST);
        }
        return response;
    }

    private Map<String, Serializable> convert(MultivaluedMap<String, String> map) {
        Map<String, Serializable> convertedMap = new HashMap<String, Serializable>();

        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            String key = entry.getKey();
            List<String> value = entry.getValue();

            if (value.size() == 1) {
                convertedMap.put(key, value.get(0));
            } else {
                // List is not serializable so we make it a String array
                convertedMap.put(key, value.toArray());
            }
        }

        return convertedMap;
    }

    private Metacard generateMetacard(MimeType mimeType, String id, InputStream message,
            String transformerId) throws MetacardCreationException {

        List<InputTransformer> listOfCandidates = mimeTypeToTransformerMapper.findMatches(
                InputTransformer.class,
                mimeType);

        LOGGER.trace("Entering generateMetacard.");
        LOGGER.debug("List of matches for mimeType [{}]: {}", mimeType, listOfCandidates);

        Metacard generatedMetacard = null;

        try (TemporaryFileBackedOutputStream fileBackedOutputStream = new TemporaryFileBackedOutputStream()) {

            try {
                if (null != message) {
                    IOUtils.copy(message, fileBackedOutputStream);
                } else {
                    throw new MetacardCreationException(
                            "Could not copy bytes of content message.  Message was NULL.");
                }
            } catch (IOException e) {
                throw new MetacardCreationException("Could not copy bytes of content message.", e);
            }

            Iterator<InputTransformer> it = listOfCandidates.iterator();
            if (StringUtils.isNotEmpty(transformerId)) {
                BundleContext bundleContext = getBundleContext();
                Collection<ServiceReference<InputTransformer>> serviceReferences = bundleContext.getServiceReferences(
                        InputTransformer.class, "(id=" + transformerId + ")");
                it = serviceReferences.stream()
                        .map(bundleContext::getService)
                        .iterator();
            }

            StringBuilder causeMessage = new StringBuilder(
                    "Could not create metacard with mimeType ");
            causeMessage.append(mimeType);
            causeMessage.append(". Reason: ");
            while (it.hasNext()) {
                InputTransformer transformer = it.next();

                try (InputStream inputStreamMessageCopy = fileBackedOutputStream.asByteSource()
                        .openStream()) {
                    generatedMetacard = transformer.transform(inputStreamMessageCopy);
                } catch (CatalogTransformerException | IOException e) {
                    causeMessage.append(System.lineSeparator());
                    causeMessage.append(e.getMessage());
                    // The caught exception more than likely does not have the root cause message
                    // that is needed to inform the caller as to why things have failed.  Therefore
                    // we need to iterate through the chain of cause exceptions and gather up
                    // all of their message details.
                    Throwable cause = e.getCause();
                    while (null != cause && cause != cause.getCause()) {
                        causeMessage.append(System.lineSeparator());
                        causeMessage.append(cause.getMessage());
                        cause = cause.getCause();
                    }
                    LOGGER.debug("Transformer [{}] could not create metacard.", transformer, e);
                }
                if (generatedMetacard != null) {
                    break;
                }
            }

            if (generatedMetacard == null) {
                throw new MetacardCreationException(causeMessage.toString());
            }

            if (id != null) {
                generatedMetacard.setAttribute(new AttributeImpl(Metacard.ID, id));
            } else {
                LOGGER.debug("Metacard had a null id");
            }
        } catch (IOException e) {
            throw new MetacardCreationException("Could not create metacard.", e);
        } catch (InvalidSyntaxException e) {
            throw new MetacardCreationException("Could not determine transformer", e);
        }
        return generatedMetacard;

    }

    private MimeType getMimeType(HttpHeaders headers) {
        List<String> contentTypeList = headers.getRequestHeader(HttpHeaders.CONTENT_TYPE);

        String singleMimeType = null;

        if (contentTypeList != null && !contentTypeList.isEmpty()) {
            singleMimeType = contentTypeList.get(0);
            LOGGER.debug("Encountered [{}] {}", singleMimeType, HttpHeaders.CONTENT_TYPE);
        }

        MimeType mimeType = null;

        // Sending a null argument to MimeType causes NPE
        if (singleMimeType != null) {
            try {
                mimeType = new MimeType(singleMimeType);
            } catch (MimeTypeParseException e) {
                LOGGER.debug("Could not parse mime type from headers.", e);
            }
        }

        return mimeType;
    }

    private String getFileExtensionForMimeType(String mimeType) {
        String fileExtension = this.tikaMimeTypeResolver.getFileExtensionForMimeType(mimeType);
        LOGGER.debug("Mime Type [{}] resolves to file extension [{}].", mimeType, fileExtension);
        return fileExtension;
    }

    private boolean rangeHeaderExists(HttpServletRequest httpRequest) {
        boolean response = false;

        if (null != httpRequest) {
            if (null != httpRequest.getHeader(HEADER_RANGE)) {
                response = true;
            }
        }

        return response;
    }

    // Return 0 (beginning of stream) if the range header does not exist.
    private long getRangeStart(HttpServletRequest httpRequest) throws UnsupportedQueryException {
        long response = 0;

        if (httpRequest != null) {
            if (rangeHeaderExists(httpRequest)) {
                String rangeHeader = httpRequest.getHeader(HEADER_RANGE);
                String range = getRange(rangeHeader);

                if (range != null) {
                    response = Long.parseLong(range);
                }
            }
        }

        return response;
    }

    private String getRange(String rangeHeader) throws UnsupportedQueryException {
        String response = null;

        if (rangeHeader != null) {
            if (rangeHeader.startsWith(BYTES_EQUAL)) {
                String tempString = rangeHeader.substring(BYTES_EQUAL.length());
                if (tempString.contains("-")) {
                    response = rangeHeader.substring(BYTES_EQUAL.length(),
                            rangeHeader.lastIndexOf("-"));
                } else {
                    response = rangeHeader.substring(BYTES_EQUAL.length());
                }
            } else {
                throw new UnsupportedQueryException("Invalid range header: " + rangeHeader);
            }
        }

        return response;
    }

    public MimeTypeToTransformerMapper getMimeTypeToTransformerMapper() {
        return mimeTypeToTransformerMapper;
    }

    public void setMimeTypeToTransformerMapper(
            MimeTypeToTransformerMapper mimeTypeToTransformerMapper) {
        this.mimeTypeToTransformerMapper = mimeTypeToTransformerMapper;
    }

    public FilterBuilder getFilterBuilder() {
        return filterBuilder;
    }

    public void setFilterBuilder(FilterBuilder filterBuilder) {
        this.filterBuilder = filterBuilder;
    }

    public void setTikaMimeTypeResolver(MimeTypeResolver mimeTypeResolver) {
        this.tikaMimeTypeResolver = mimeTypeResolver;
    }

    public void setMimeTypeMapper(MimeTypeMapper mimeTypeMapper) {
        this.mimeTypeMapper = mimeTypeMapper;
    }

    public void setMetacardTypes(List<MetacardType> metacardTypes) {
        this.metacardTypes = metacardTypes;
    }

    protected static class CreateInfo {
        InputStream stream = null;

        String filename = null;

        String contentType = null;

        Metacard metacard = null;

        public InputStream getStream() {
            return stream;
        }

        public void setStream(InputStream stream) {
            this.stream = stream;
        }

        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public Metacard getMetacard() {
            return metacard;
        }

        public void setMetacard(Metacard metacard) {
            this.metacard = metacard;
        }
    }

    protected static class IncomingContentItem extends ContentItemImpl {

        private InputStream inputStream;

        public IncomingContentItem(ByteSource byteSource, String mimeTypeRawData, String filename,
                Metacard metacard) {
            super(byteSource, mimeTypeRawData, filename, metacard);
        }

        public IncomingContentItem(InputStream inputStream, String mimeTypeRawData, String filename,
                Metacard metacard) {
            super(null, mimeTypeRawData, filename, metacard);
            this.inputStream = inputStream;
        }

        public IncomingContentItem(String id, InputStream inputStream, String mimeTypeRawData,
                String filename, long size, Metacard metacard) {
            super(id, null, mimeTypeRawData, filename, size, metacard);
            this.inputStream = inputStream;
        }

        @Override
        public InputStream getInputStream() {
            return inputStream;
        }
    }
}
