/*******************************************************************************
 * Copyright (C) 2014 The Calrissian Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package org.calrissian.restdoclet.collector;


import com.sun.source.util.DocTrees;
import org.calrissian.restdoclet.model.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;

import static java.util.Collections.emptyList;
import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.NoType;
import javax.lang.model.type.TypeMirror;
import jdk.javadoc.doclet.DocletEnvironment;
import static org.calrissian.restdoclet.util.CommonUtils.*;
import static org.calrissian.restdoclet.util.TagUtils.*;

public abstract class AbstractCollector implements Collector {

    protected final DocTrees treeUtils;

    public AbstractCollector(DocTrees treeUtils) {
        this.treeUtils = treeUtils;
    }

    protected abstract boolean shouldIgnoreClass(TypeElement classDoc);
    protected abstract boolean shouldIgnoreMethod(ExecutableElement methodDoc);
    protected abstract EndpointMapping getEndpointMapping(Element doc);
    protected abstract Collection<PathVar> generatePathVars(ExecutableElement methodDoc);
    protected abstract Collection<QueryParam> generateQueryParams(ExecutableElement methodDoc);
    protected abstract RequestBody generateRequestBody(ExecutableElement methodDoc);

    /**
     * Will generate and aggregate all the rest endpoint class descriptors.
     * @param rootDoc
     * @return
     */
    @Override
    public Collection<ClassDescriptor> getDescriptors(DocletEnvironment rootDoc) {
        Collection<ClassDescriptor> classDescriptors = new ArrayList<>();

        //Loop through all of the classes and if it contains endpoints then add it to the set of descriptors.
        for (Element e : rootDoc.getIncludedElements()) {
            if (e instanceof TypeElement) {
                TypeElement classDoc = (TypeElement) e;
                ClassDescriptor descriptor = getClassDescriptor(classDoc);
                if (descriptor != null && !isEmpty(descriptor.getEndpoints())) {
                    classDescriptors.add(descriptor);
                }
            }
        }
        return classDescriptors;
    }

    /**
     * Will generate a single class descriptor and all the endpoints for that class.
     *
     * If any class contains the special javadoc tag {@link org.calrissian.restdoclet.util.TagUtils.IGNORE_TAG} it will be excluded.
     * @param classDoc
     * @return
     */
    protected ClassDescriptor getClassDescriptor(TypeElement classDoc) {

        //If the ignore tag is present or this type of class should be ignored then simply ignore this class
        if (!isEmpty(getTags(classDoc, IGNORE_TAG, treeUtils)) || shouldIgnoreClass(classDoc)) {
            return null;
        }
        String contextPath = getContextPath(classDoc);
        Collection<Endpoint> endpoints = getAllEndpoints(contextPath, classDoc, getEndpointMapping(classDoc));

        //If there are no endpoints then no use in providing documentation.
        if (isEmpty(endpoints)) {
            return null;
        }

        String name = getClassName(classDoc);
        String description = getClassDescription(classDoc);

        return new ClassDescriptor(
                (name == null ? "" : name),
                (contextPath == null ? "" : contextPath),
                endpoints,
                (description == null ? "" : description)
        );
    }

    /**
     * Retrieves all the end point provided in the specified class doc.
     * @param contextPath
     * @param classDoc
     * @param classMapping
     * @return
     */
    protected Collection<Endpoint> getAllEndpoints(String contextPath, TypeElement classDoc, EndpointMapping classMapping) {
        Collection<Endpoint> endpoints = new ArrayList<>();

        for (ExecutableElement method : getMethods(classDoc)) {
            endpoints.addAll(getEndpoint(contextPath, classMapping, method));
        }

        //Check super classes for inherited methods
        TypeMirror superClass = classDoc.getSuperclass();
        if (superClass != null && !(superClass instanceof NoType)) {
            TypeElement te = asTypeElement(classDoc.getSuperclass());
            if (te != null) {
                endpoints.addAll(getAllEndpoints(contextPath, te, classMapping));
            }
        }
        return endpoints;
    }

    /**
     * Retrieves the endpoint for a single method.
     *
     * If any method contains the special javadoc tag {@link org.calrissian.restdoclet.util.TagUtils.IGNORE_TAG} it will be excluded.
     * @param contextPath
     * @param classMapping
     * @param method
     * @return
     */
    protected Collection<Endpoint> getEndpoint(String contextPath, EndpointMapping classMapping, ExecutableElement method) {

        //If the ignore tag is present then simply return nothing for this endpoint.
        if (!isEmpty(getTags(method, IGNORE_TAG, treeUtils)) || shouldIgnoreMethod(method))
            return emptyList();

        Collection<Endpoint> endpoints = new ArrayList<>();
        EndpointMapping methodMapping = getEndpointMapping(method);

        Collection<String> paths = resolvePaths(contextPath, classMapping, methodMapping);
        Collection<String> httpMethods = resolveHttpMethods(classMapping, methodMapping);
        Collection<String> consumes = resolveConsumesInfo(classMapping, methodMapping);
        Collection<String> produces = resolvesProducesInfo(classMapping, methodMapping);
        Collection<PathVar> pathVars = generatePathVars(method);
        Collection<QueryParam> queryParams = generateQueryParams(method);
        RequestBody requestBody = generateRequestBody(method);
        String firstSentence = firstSentence(method, treeUtils);
        String body = fullBody(method, treeUtils);

        for (String httpMethod : httpMethods) {
            for (String path : paths) {
                Endpoint ep = new Endpoint(
                                path,
                                httpMethod,
                                queryParams,
                                pathVars,
                                requestBody,
                                consumes,
                                produces,
                                firstSentence,
                                body,
                                method.getReturnType());
                endpoints.add(ep);
            }
        }

        return endpoints;
    }

    /**
     * Will get the initial context path to use for all rest endpoint.
     *
     * This looks for the value in a special javadoc tag {@link org.calrissian.restdoclet.util.TagUtils.CONTEXT_TAG}
     *
     * @param classDoc
     * @return
     */
    protected String getContextPath(TypeElement classDoc) {
        List<String> tags = getTags(classDoc, CONTEXT_TAG, treeUtils);
        if(!isEmpty(tags)) {
            return tags.get(0);
        }
        return "";
    }

    /**
     * Will get the display name for the class.
     *
     * This looks for the value in a special javadoc tag {@link org.calrissian.restdoclet.util.TagUtils.NAME_TAG}
     *
     * @param classDoc
     * @return
     */
    protected String getClassName(TypeElement classDoc) {
        List<String> tags = getTags(classDoc, NAME_TAG, treeUtils);
        if(!isEmpty(tags)) {
            return tags.get(0);
        }
        return classDoc.getQualifiedName().toString();
    }

    /**
     * Will get the description for the class.
     * @param classDoc
     * @return
     */
    protected String getClassDescription(TypeElement classDoc) {
        return fullBody(classDoc, treeUtils);
    }

    /**
     * Will generate all the paths specified in the class and method mappings.
     * Each path should start with the context path, followed by one of the class paths,
     * then finally the method path.
     *
     * @param contextPath
     * @param classMapping
     * @param methodMapping
     * @return
     */
    protected Collection<String> resolvePaths(String contextPath, EndpointMapping classMapping, EndpointMapping methodMapping) {

        contextPath = (contextPath == null ? "" : contextPath);

        //Build all the paths based on the class level, plus the method extensions.
        LinkedHashSet<String> paths = new LinkedHashSet<>();

        if (isEmpty(classMapping.getPaths())) {
            for (String path : methodMapping.getPaths()) {
                paths.add(fixPath(contextPath + path));
            }
        } else if (isEmpty(methodMapping.getPaths())) {
            for (String path : classMapping.getPaths()) {
                paths.add(fixPath(contextPath + path));
            }
        } else {
            for (String defaultPath : classMapping.getPaths()) {
                for (String path : methodMapping.getPaths()) {
                    paths.add(fixPath(contextPath + defaultPath + path));
                }
            }
        }
        return paths;
    }

    /**
     * Will use the method's mapped information if it is not empty, otherwise it will use the class mapping information
     * to retrieve all the https methods.
     * @param classMapping
     * @param methodMapping
     * @return
     */
    protected Collection<String> resolveHttpMethods(EndpointMapping classMapping, EndpointMapping methodMapping) {
        return firstNonEmpty(
                methodMapping.getHttpMethods(),
                classMapping.getHttpMethods()
        );
    }

    /**
     * Will use the method's mapped information if it is not empty, otherwise it will use the class mapping information
     * to retrieve all the consumeable information.
     * @param classMapping
     * @param methodMapping
     * @return
     */
    protected Collection<String> resolveConsumesInfo(EndpointMapping classMapping, EndpointMapping methodMapping) {
        return firstNonEmpty(
                methodMapping.getConsumes(),
                classMapping.getConsumes()
        );
    }

    /**
     * Will use the method's mapped information if it is not empty, otherwise it will use the class mapping information
     * to retrieve all the produceable information.
     * @param classMapping
     * @param methodMapping
     * @return
     */
    protected Collection<String> resolvesProducesInfo(EndpointMapping classMapping, EndpointMapping methodMapping) {
        return firstNonEmpty(
                methodMapping.getProduces(),
                classMapping.getProduces()
        );
    }
}
