/*
 * Copyright 2016 Crown Copyright
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

package uk.gov.gchq.gaffer.store.schema;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.gchq.gaffer.data.element.IdentifierType;
import uk.gov.gchq.gaffer.data.element.function.ElementFilter;
import uk.gov.gchq.gaffer.data.element.function.ElementTransformer;
import uk.gov.gchq.gaffer.data.elementdefinition.view.View;
import uk.gov.gchq.gaffer.data.elementdefinition.view.ViewElementDefinition;
import uk.gov.gchq.koryphe.ValidationResult;
import uk.gov.gchq.koryphe.signature.Signature;
import uk.gov.gchq.koryphe.tuple.function.TupleAdaptedFunction;
import uk.gov.gchq.koryphe.tuple.predicate.TupleAdaptedPredicate;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * An <code>ViewValidator</code> validates a view against a {@link Schema}
 * {@link uk.gov.gchq.gaffer.data.elementdefinition.view.ViewElementDefinition}.
 * Checks all function input and output types are compatible with the
 * properties and identifiers in the Schema and the transient properties in the
 * View.
 */
public class ViewValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(ViewValidator.class);

    /**
     * Checks all {@link java.util.function.Predicate}s and {@link java.util.function.Function}s defined are
     * compatible with the identifiers and properties in the {@link Schema}
     * and transient properties in the {@link View}.
     *
     * @param view           the {@link View} to validate
     * @param schema         the {@link Schema} to validate the view against
     * @param isStoreOrdered true if the store is ordered
     * @return true if the element definition is valid, otherwise false and an error is logged
     */
    public ValidationResult validate(final View view, final Schema schema, final boolean isStoreOrdered) {
        final ValidationResult result = new ValidationResult();

        if (null != view) {
            if (null != view.getEntities()) {
                for (final Map.Entry<String, ViewElementDefinition> entry : view.getEntities().entrySet()) {
                    final String group = entry.getKey();
                    final SchemaEntityDefinition schemaElDef = schema.getEntity(group);
                    final ViewElementDefinition viewElDef = entry.getValue();

                    if (null == schemaElDef) {
                        result.addError("Entity group " + group + " does not exist in the schema");
                    } else {
                        for (final String transProp : viewElDef.getTransientProperties()) {
                            if (schemaElDef.containsProperty(transProp)) {
                                result.addError("Transient property " + transProp + " for entity group " + group + " is not transient as it has been found in the schema");
                            }
                        }

                        result.add(validateFunctionArgumentTypes(viewElDef.getPreAggregationFilter(), viewElDef, schemaElDef));
                        result.add(validateFunctionArgumentTypes(viewElDef.getPostAggregationFilter(), viewElDef, schemaElDef));
                        result.add(validateFunctionArgumentTypes(viewElDef.getTransformer(), viewElDef, schemaElDef));
                        result.add(validateFunctionArgumentTypes(viewElDef.getPostTransformFilter(), viewElDef, schemaElDef));
                        result.add(validateGroupBy(isStoreOrdered, group, viewElDef, schemaElDef));
                    }
                }
            }

            if (null != view.getEdges()) {
                for (final Map.Entry<String, ViewElementDefinition> entry : view.getEdges().entrySet()) {
                    final String group = entry.getKey();
                    final SchemaEdgeDefinition schemaElDef = schema.getEdge(group);
                    final ViewElementDefinition viewElDef = entry.getValue();

                    if (null == schemaElDef) {
                        result.addError("Edge group " + group + " does not exist in the schema");
                    } else {
                        for (final String transProp : viewElDef.getTransientProperties()) {
                            if (schemaElDef.containsProperty(transProp)) {
                                result.addError("Transient property " + transProp + " for edge group " + group + " is not transient as it has been found in the schema");
                            }
                        }

                        result.add(validateFunctionArgumentTypes(viewElDef.getPreAggregationFilter(), viewElDef, schemaElDef));
                        result.add(validateFunctionArgumentTypes(viewElDef.getPostAggregationFilter(), viewElDef, schemaElDef));
                        result.add(validateFunctionArgumentTypes(viewElDef.getTransformer(), viewElDef, schemaElDef));
                        result.add(validateFunctionArgumentTypes(viewElDef.getPostTransformFilter(), viewElDef, schemaElDef));
                        result.add(validateGroupBy(isStoreOrdered, group, viewElDef, schemaElDef));
                    }
                }
            }
        }

        return result;
    }

    protected ValidationResult validateGroupBy(final boolean isStoreOrdered, final String group, final ViewElementDefinition viewElDef, final SchemaElementDefinition schemaElDef) {
        final ValidationResult result = new ValidationResult();
        final Set<String> viewGroupBy = viewElDef.getGroupBy();
        if (null != viewGroupBy && !viewGroupBy.isEmpty()) {
            final Set<String> schemaGroupBy = schemaElDef.getGroupBy();
            if (null != schemaGroupBy && schemaGroupBy.containsAll(viewGroupBy)) {
                if (isStoreOrdered) {
                    final LinkedHashSet<String> schemaGroupBySubset = Sets.newLinkedHashSet(Iterables.limit(schemaGroupBy, viewGroupBy.size()));
                    if (!viewGroupBy.equals(schemaGroupBySubset)) {
                        result.addError("Group by properties for group " + group + " are not in the same order as the group by properties in the schema. View groupBy:" + viewGroupBy + ". Schema groupBy:" + schemaGroupBy);
                    }
                }
            } else {
                result.addError("Group by properties for group " + group + " in the view are not all included in the group by field in the schema. View groupBy:" + viewGroupBy + ". Schema groupBy:" + schemaGroupBy);
            }
        }

        return result;
    }

    private ValidationResult validateFunctionArgumentTypes(
            final ElementFilter filter,
            final ViewElementDefinition viewElDef, final SchemaElementDefinition schemaElDef) {
        final ValidationResult result = new ValidationResult();
        if (null != filter && null != filter.getFunctions()) {
            for (final TupleAdaptedPredicate<String, ?> adaptedPredicate : filter.getFunctions()) {
                if (null == adaptedPredicate.getFunction()) {
                    result.addError(filter.getClass().getSimpleName() + " contains a null function.");
                } else {
                    final Signature inputSig = Signature.getInputSignature(adaptedPredicate.getFunction());
                    result.add(inputSig.assignable(getTypeClasses(adaptedPredicate.getSelection(), viewElDef, schemaElDef)));
                }
            }
        }

        return result;
    }

    private ValidationResult validateFunctionArgumentTypes(
            final ElementTransformer transformer,
            final ViewElementDefinition viewElDef, final SchemaElementDefinition schemaElDef) {
        final ValidationResult result = new ValidationResult();
        if (null != transformer && null != transformer.getFunctions()) {
            for (final TupleAdaptedFunction<String, ?, ?> adaptedFunction : transformer.getFunctions()) {
                if (null == adaptedFunction.getFunction()) {
                    result.addError(transformer.getClass().getSimpleName() + " contains a null function.");
                } else {
                    final Signature inputSig = Signature.getInputSignature(adaptedFunction.getFunction());
                    result.add(inputSig.assignable(getTypeClasses(adaptedFunction.getSelection(), viewElDef, schemaElDef)));

                    final Signature outputSig = Signature.getOutputSignature(adaptedFunction.getFunction());
                    result.add(outputSig.assignable(getTypeClasses(adaptedFunction.getProjection(), viewElDef, schemaElDef)));
                }
            }
        }

        return result;
    }

    private Class[] getTypeClasses(final String[] keys, final ViewElementDefinition viewElDef, final SchemaElementDefinition schemaElDef) {
        final Class[] selectionClasses = new Class[keys.length];
        int i = 0;
        for (final String key : keys) {
            selectionClasses[i] = getTypeClass(key, viewElDef, schemaElDef);
            i++;
        }
        return selectionClasses;
    }

    private Class<?> getTypeClass(final String key, final ViewElementDefinition viewElDef, final SchemaElementDefinition schemaElDef) {
        final IdentifierType idType = IdentifierType.fromName(key);
        final Class<?> clazz;
        if (null != idType) {
            clazz = schemaElDef.getIdentifierClass(idType);
        } else {
            final Class<?> schemaClazz = schemaElDef.getPropertyClass(key);
            if (null != schemaClazz) {
                clazz = schemaClazz;
            } else {
                clazz = viewElDef.getTransientPropertyClass(key);
            }
        }
        if (null == clazz) {
            if (null != idType) {
                final String typeName = schemaElDef.getIdentifierTypeName(idType);
                if (null != typeName) {
                    LOGGER.error("No class type found for type definition " + typeName
                            + " used by identifier " + idType
                            + ". Please ensure it is defined in the schema.");
                } else {
                    LOGGER.error("No type definition defined for identifier " + idType
                            + ". Please ensure it is defined in the schema.");
                }
            } else {
                final String typeName = schemaElDef.getPropertyTypeName(key);
                if (null != typeName) {
                    LOGGER.error("No class type found for type definition " + typeName
                            + " used by property " + key
                            + ". Please ensure it is defined in the schema.");
                } else {
                    LOGGER.error("No class type found for transient property " + key
                            + ". Please ensure it is defined in the view.");
                }
            }

        }
        return clazz;
    }
}
