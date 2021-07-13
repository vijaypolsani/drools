/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates.
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
 */
package org.kie.pmml.evaluator.core.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.kie.api.pmml.PMMLRequestData;
import org.kie.api.pmml.ParameterInfo;
import org.kie.pmml.api.runtime.PMMLContext;
import org.kie.pmml.commons.model.KiePMMLModel;
import org.kie.pmml.commons.model.expressions.KiePMMLExpression;
import org.kie.pmml.commons.model.tuples.KiePMMLNameValue;
import org.kie.pmml.commons.transformations.KiePMMLDefineFunction;
import org.kie.pmml.commons.transformations.KiePMMLDerivedField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class meant to provide static methods related to <b>pre-process</b> manipulation
 */
public class PreProcess {

    private static final Logger logger = LoggerFactory.getLogger(PreProcess.class);

    private PreProcess() {
        // Avoid instantiation
    }

    public static List<KiePMMLNameValue> preProcess(final KiePMMLModel model, final PMMLContext context) {
        addMissingValuesReplacements(model, context);
        final PMMLRequestData requestData = context.getRequestData();
        final Map<String, ParameterInfo> mappedRequestParams = requestData.getMappedRequestParams();
        final List<KiePMMLNameValue> kiePMMLNameValues =
                getKiePMMLNameValuesFromParameterInfos(mappedRequestParams.values());
        executeTransformations(model, requestData, kiePMMLNameValues);
        return kiePMMLNameValues;
    }

    /**
     * Add missing input values if defined in original PMML as <b>missingValueReplacement</b>.
     * <p>
     * "missingValueReplacement: If this attribute is specified then a missing input value is automatically replaced
     * by the given value.
     * That is, the model itself works as if the given value was found in the original input. "
     * @param model
     * @param context
     * @see
     * <a href="http://dmg.org/pmml/v4-4/MiningSchema.html#xsdType_MISSING-VALUE-TREATMENT-METHOD">MISSING-VALUE-TREATMENT-METHOD</a>
     */
    static void addMissingValuesReplacements(final KiePMMLModel model, final PMMLContext context) {
        logger.debug("addMissingValuesReplacements {} {}", model, context);
        final PMMLRequestData requestData = context.getRequestData();
        final Map<String, ParameterInfo> mappedRequestParams = requestData.getMappedRequestParams();
        final Map<String, Object> missingValueReplacementMap = model.getMissingValueReplacementMap();
        missingValueReplacementMap.forEach((fieldName, missingValueReplacement) -> {
            if (!mappedRequestParams.containsKey(fieldName)) {
                logger.debug("missingValueReplacement {} {}", fieldName, missingValueReplacement);
                requestData.addRequestParam(fieldName, missingValueReplacement);
                context.addMissingValueReplaced(fieldName, missingValueReplacement);
            }
        });
    }

    /**
     * Execute <b>transformations</b> on input data.
     * @param model
     * @param requestData
     * @param kiePMMLNameValues
     * @see <a href="http://dmg.org/pmml/v4-4/Transformations.html">Transformations</a>
     * @see
     * <a href="http://dmg.org/pmml/v4-4/Transformations.html#xsdElement_LocalTransformations">LocalTransformations</a>
     */
    static void executeTransformations(final KiePMMLModel model,
                                       final PMMLRequestData requestData,
                                       final List<KiePMMLNameValue> kiePMMLNameValues) {
        logger.debug("executeTransformations {} {}", model, requestData);
        List<KiePMMLDerivedField> derivedFields = new ArrayList<>();
        List<KiePMMLDefineFunction> defineFunctions = new ArrayList<>();
        if (model.getTransformationDictionary() != null) {
            if (model.getTransformationDictionary().getDerivedFields() != null) {
                derivedFields.addAll(model.getTransformationDictionary().getDerivedFields());
            }
            if (model.getTransformationDictionary().getDefineFunctions() != null) {
                defineFunctions.addAll(model.getTransformationDictionary().getDefineFunctions());
            }
        }
        if (model.getLocalTransformations() != null && model.getLocalTransformations().getDerivedFields() != null) {
            derivedFields.addAll(model.getLocalTransformations().getDerivedFields());
        }
        for (KiePMMLDerivedField derivedField : derivedFields) {
            Object derivedValue = derivedField.evaluate(defineFunctions, derivedFields, Collections.emptyList(),  kiePMMLNameValues);
            if (derivedValue != null) {
                requestData.addRequestParam(derivedField.getName(), derivedValue);
                kiePMMLNameValues.add(new KiePMMLNameValue(derivedField.getName(), derivedValue));
            }
        }
    }

    static List<KiePMMLNameValue> getKiePMMLNameValuesFromParameterInfos(final Collection<ParameterInfo> parameterInfos) {
        return parameterInfos.stream()
                .map(parameterInfo -> new KiePMMLNameValue(parameterInfo.getName(), parameterInfo.getValue()))
                .collect(Collectors.toList());
    }
}
