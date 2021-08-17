/*
 * Copyright 2016-2020 Crown Copyright
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

package uk.gov.gchq.gaffer.accumulostore.operation.handler;

import uk.gov.gchq.gaffer.accumulostore.AccumuloStore;
import uk.gov.gchq.gaffer.accumulostore.key.IteratorSettingFactory;
import uk.gov.gchq.gaffer.accumulostore.key.exception.IteratorSettingException;
import uk.gov.gchq.gaffer.accumulostore.retriever.impl.AccumuloRangeIDRetriever;
import uk.gov.gchq.gaffer.commonutil.iterable.CloseableIterable;
import uk.gov.gchq.gaffer.data.element.Element;
import uk.gov.gchq.gaffer.operation.OperationException;
import uk.gov.gchq.gaffer.store.Context;
import uk.gov.gchq.gaffer.store.Store;
import uk.gov.gchq.gaffer.store.StoreException;
import uk.gov.gchq.gaffer.store.operation.handler.OutputOperationHandler;
import uk.gov.gchq.gaffer.user.User;

public class GetElementsInRangesHandler implements OutputOperationHandler<GetElementsInRanges, CloseableIterable<? extends Element>> {

    @Override
    public CloseableIterable<? extends Element> doOperation(final GetElementsInRanges operation,
                                                            final Context context, final Store store)
            throws OperationException {
        return doOperation(operation, context.getUser(), (AccumuloStore) store);
    }

    public CloseableIterable<? extends Element> doOperation(final GetElementsInRanges operation,
                                                            final User user,
                                                            final AccumuloStore store) throws OperationException {
        final IteratorSettingFactory itrFactory = store.getKeyPackage().getIteratorFactory();
        try {
            return new AccumuloRangeIDRetriever<>(store, operation, user,
                    itrFactory.getElementPreAggregationFilterIteratorSetting(operation.getView(), store),
                    itrFactory.getElementPostAggregationFilterIteratorSetting(operation.getView(), store),
                    itrFactory.getEdgeEntityDirectionFilterIteratorSetting(operation),
                    itrFactory.getElementPropertyRangeQueryFilter(operation),
                    itrFactory.getQueryTimeAggregatorIteratorSetting(operation.getView(), store));
        } catch (final IteratorSettingException | StoreException e) {
            throw new OperationException("Failed to get elements", e);
        }
    }
}
