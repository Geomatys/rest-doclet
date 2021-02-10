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
package org.calrissian.restdoclet.collector.spring;


import com.sun.source.util.DocTrees;
import org.calrissian.restdoclet.collector.AbstractCollector;
import org.calrissian.restdoclet.collector.EndpointMapping;
import org.calrissian.restdoclet.model.PathVar;
import org.calrissian.restdoclet.model.QueryParam;
import org.calrissian.restdoclet.model.RequestBody;

import java.util.*;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.util.Arrays.asList;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import static org.calrissian.restdoclet.util.AnnotationUtils.getAnnotationName;
import static org.calrissian.restdoclet.util.AnnotationUtils.getElementValue;
import static org.calrissian.restdoclet.util.CommonUtils.firstNonEmpty;
import static org.calrissian.restdoclet.util.CommonUtils.isEmpty;
import static org.calrissian.restdoclet.util.TagUtils.*;

public class SpringCollector extends AbstractCollector {

    protected static final List<String> CONTROLLER_ANNOTATION = Arrays.asList("org.springframework.stereotype.Controller",
                                                                        "org.springframework.web.bind.annotation.RestController");
    protected static final String MAPPING_ANNOTATION = "org.springframework.web.bind.annotation.RequestMapping";
    protected static final String PATHVAR_ANNOTATION = "org.springframework.web.bind.annotation.PathVariable";
    protected static final String PARAM_ANNOTATION = "org.springframework.web.bind.annotation.RequestParam";
    protected static final String REQUESTBODY_ANNOTATION = "org.springframework.web.bind.annotation.RequestBody";

    public SpringCollector(DocTrees treeUtils) {
        super(treeUtils);
    }
    
    @Override
    protected boolean shouldIgnoreClass(TypeElement classDoc) {
        //If found a controller annotation then don't ignore this class.
        for (AnnotationMirror classAnnotation : classDoc.getAnnotationMirrors()) {
            if (CONTROLLER_ANNOTATION.contains(getAnnotationName(classAnnotation))) {
                return false;
            }
        }
        //If not found then ignore this class.
        return true;
    }

    @Override
    protected boolean shouldIgnoreMethod(ExecutableElement methodDoc) {
        //If found a mapping annotation then don't ignore this class.
        for (AnnotationMirror classAnnotation : methodDoc.getAnnotationMirrors())
            if (MAPPING_ANNOTATION.equals(getAnnotationName(classAnnotation)))
                return false;

        //If not found then ignore this class.
        return true;
    }

    @Override
    protected EndpointMapping getEndpointMapping(Element doc) {
        //Look for a request mapping annotation
        for (AnnotationMirror annotation : doc.getAnnotationMirrors()) {
            //If found then extract the value (paths) and the methods.
            if (MAPPING_ANNOTATION.equals(getAnnotationName(annotation))) {

                //Get http methods from annotation
                Collection<String> httpMethods = new LinkedHashSet<>();
                for (String value : getElementValue(annotation, "method")) {
                    httpMethods.add(value.substring(value.lastIndexOf(".") + 1));
                }

                return new EndpointMapping(
                        new LinkedHashSet<>(getElementValue(annotation, "value")),
                        httpMethods,
                        new LinkedHashSet<>(getElementValue(annotation, "consumes")),
                        new LinkedHashSet<>(getElementValue(annotation, "produces"))
                );
            }
        }

        //Simply return an empty grouping if no request mapping was found.
        return new EndpointMapping(
                Collections.<String>emptySet(),
                Collections.<String>emptySet(),
                Collections.<String>emptySet(),
                Collections.<String>emptySet()
        );
    }

    @Override
    protected Collection<PathVar> generatePathVars(ExecutableElement methodDoc) {
        Collection<PathVar> retVal = new ArrayList<>();

        List<String> tags = getTags(methodDoc, PATHVAR_TAG, treeUtils);
        Map<String, List<String>> paramTags = getParams(methodDoc, treeUtils);

        for (VariableElement parameter : methodDoc.getParameters()) {
            for (AnnotationMirror annotation : parameter.getAnnotationMirrors()) {
                if (getAnnotationName(annotation).equals(PATHVAR_ANNOTATION)) {
                    String name = parameter.getSimpleName().toString();
                    Collection<String> values = getElementValue(annotation, "value");
                    if (!values.isEmpty()) {
                        name = values.iterator().next();
                    }

                    //first check for special tag, then check regular param tag, finally default to empty string
                    String text = findParamText(tags, name);
                    if (text == null) {
                        String paramName = parameter.getSimpleName().toString();
                        if (paramTags.containsKey(paramName) && !paramTags.get(paramName).isEmpty()) {
                            text = paramTags.get(paramName).get(0);
                        }
                    }
                    if (text == null) {
                        text = "";
                    }
                    retVal.add(new PathVar(name, text, parameter.asType()));
                }
            }
        }

        return retVal;
    }

    @Override
    protected Collection<QueryParam> generateQueryParams(ExecutableElement methodDoc) {
        Collection<QueryParam> retVal = new ArrayList<> ();

        List<String> tags = getTags(methodDoc, QUERYPARAM_TAG, treeUtils);
        Map<String, List<String>> paramTags = getParams(methodDoc, treeUtils);

        for (VariableElement parameter : methodDoc.getParameters()) {
            for (AnnotationMirror annotation : parameter.getAnnotationMirrors()) {
                if (getAnnotationName(annotation).equals(PARAM_ANNOTATION)) {
                    String name = parameter.getSimpleName().toString();
                    List<String> values = getElementValue(annotation, "value");
                    if (!values.isEmpty())
                        name = values.get(0);

                    List<String> requiredVals = getElementValue(annotation, "required");

                    //With spring query params are required by default
                    boolean required = TRUE;
                    if (!requiredVals.isEmpty()) {
                        required = Boolean.parseBoolean(requiredVals.get(0));
                    }

                    //With spring, if defaultValue is provided then "required" is set to false automatically
                    List<String> defaultVals = getElementValue(annotation, "defaultValue");

                    if (!defaultVals.isEmpty()) {
                        required = FALSE;
                    }

                    //first check for special tag, then check regular param tag, finally default to empty string
                    String text = findParamText(tags, name);
                    if (text == null) {
                        String paramName = parameter.getSimpleName().toString();
                        if (paramTags.containsKey(paramName) && !paramTags.get(paramName).isEmpty()) {
                            text = paramTags.get(paramName).get(0);
                        }
                    }
                    if (text == null) {
                        text = "";
                    }

                    retVal.add(new QueryParam(name, required, text, parameter.asType()));
                }
            }
        }
        return retVal;
    }

    @Override
    protected RequestBody generateRequestBody(ExecutableElement methodDoc) {

        List<String> tags = getTags(methodDoc, REQUESTBODY_TAG, treeUtils);
        Map<String, List<String>> paramTags = getParams(methodDoc, treeUtils);

        for (VariableElement parameter : methodDoc.getParameters()) {
            for (AnnotationMirror annotation : parameter.getAnnotationMirrors()) {
                if (getAnnotationName(annotation).equals(REQUESTBODY_ANNOTATION)) {

                    //first check for special tag, then check regular param tag, finally default to empty string
                    String text = (isEmpty(tags) ? null : tags.get(0));
                    if (text == null) {
                        String paramName = parameter.getSimpleName().toString();
                        if (paramTags.containsKey(paramName) && !paramTags.get(paramName).isEmpty()) {
                            text = paramTags.get(paramName).get(0);
                        }
                    }
                    if (text == null) {
                        text = "";
                    }

                    return new RequestBody(parameter.getSimpleName().toString(), text, parameter.asType());
                }
            }
        }
        return null;
    }

    @Override
    protected Collection<String> resolveHttpMethods(EndpointMapping classMapping, EndpointMapping methodMapping) {
        //If there are no http methods defined simply use GET
        return firstNonEmpty(super.resolveHttpMethods(classMapping, methodMapping), asList("GET"));
    }
}
