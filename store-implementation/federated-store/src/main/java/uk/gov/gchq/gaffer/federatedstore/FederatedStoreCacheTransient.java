/*
 * Copyright 2023 Crown Copyright
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

package uk.gov.gchq.gaffer.federatedstore;

import uk.gov.gchq.gaffer.cache.Cache;
import uk.gov.gchq.gaffer.cache.exception.CacheOperationException;
import uk.gov.gchq.gaffer.commonutil.pair.Pair;
import uk.gov.gchq.gaffer.graph.Graph;
import uk.gov.gchq.gaffer.graph.GraphSerialisable;

import java.util.Locale;
import java.util.Set;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * Wrapper around the {@link uk.gov.gchq.gaffer.cache.CacheServiceLoader} to provide an interface for
 * handling the {@link Graph}s within a {@link FederatedStore}.
 */
public class FederatedStoreCacheTransient extends Cache<String, Pair<GraphSerialisable, byte[]>> {
    public static final String ERROR_ADDING_GRAPH_TO_CACHE_GRAPH_ID_S = "Error adding graph to cache. graphId: %s";
    private static final String CACHE_SERVICE_NAME_PREFIX = "federatedStoreGraphs";

    public FederatedStoreCacheTransient() {
        this(null);
    }

    public FederatedStoreCacheTransient(final String cacheNameSuffix) {
        super(getCacheNameFrom(cacheNameSuffix));
    }

    public static String getCacheNameFrom(final String cacheNameSuffix) {
        return String.format("%s%s", CACHE_SERVICE_NAME_PREFIX,
                nonNull(cacheNameSuffix)
                        ? "_" + cacheNameSuffix.toLowerCase(Locale.UK)
                        : "");
    }

    /**
     * Get all the ID's related to the {@link Graph}'s stored in the cache.
     *
     * @return all the Graph ID's within the cache as unmodifiable set.
     */
    public Set<String> getAllGraphIds() {
        return super.getAllKeys();
    }

    /**
     * Add the specified {@link Graph} to the cache.
     *
     * @param graph     the {@link Graph} to be added
     * @param overwrite if true, overwrite any graphs already in the cache with the same ID
     * @param access    Access for the graph being stored.
     * @throws CacheOperationException if there was an error trying to add to the cache
     */
    public void addGraphToCache(final Graph graph, final byte[] access, final boolean overwrite) throws CacheOperationException {
        addGraphToCache(new GraphSerialisable.Builder(graph).build(), access, overwrite);
    }

    /**
     * Add the specified {@link Graph} to the cache.
     *
     * @param graphSerialisable the serialised {@link Graph} to be added
     * @param access            Access for the graph being stored.
     * @param overwrite         if true, overwrite any graphs already in the cache with the same ID
     * @throws CacheOperationException if there was an error trying to add to the cache
     */
    public void addGraphToCache(final GraphSerialisable graphSerialisable, final byte[] access, final boolean overwrite) throws CacheOperationException {
        String graphId = graphSerialisable.getGraphId();
        Pair<GraphSerialisable, byte[]> pair = new Pair<>(graphSerialisable, access);
        try {
            addToCache(graphId, pair, overwrite);
        } catch (final CacheOperationException e) {
            throw new CacheOperationException(String.format(ERROR_ADDING_GRAPH_TO_CACHE_GRAPH_ID_S, graphId), e);
        }
    }

    public void deleteGraphFromCache(final String graphId) {
        super.deleteFromCache(graphId);
    }

    /**
     * Retrieve the {@link GraphSerialisable} with the specified ID from the cache.
     *
     * @param graphId the ID of the {@link Graph} to retrieve
     * @return the {@link GraphSerialisable} related to the specified ID
     * @throws CacheOperationException exception
     */
    public GraphSerialisable getGraphFromCache(final String graphId) throws CacheOperationException {
        final GraphSerialisable graphSerialisable = getGraphSerialisableFromCache(graphId);
        return (isNull(graphSerialisable)) ? null : graphSerialisable;
    }

    /**
     * Retrieve the {@link Graph} with the specified ID from the cache.
     *
     * @param graphId the ID of the {@link Graph} to retrieve
     * @return the {@link Graph} related to the specified ID
     * @throws CacheOperationException exception
     */
    public GraphSerialisable getGraphSerialisableFromCache(final String graphId) throws CacheOperationException {
        final Pair<GraphSerialisable, byte[]> fromCache = getFromCache(graphId);
        return (isNull(fromCache)) ? null : fromCache.getFirst();
    }

    public byte[] getAccessFromCache(final String graphId) throws CacheOperationException {
        final Pair<GraphSerialisable, byte[]> fromCache = getFromCache(graphId);
        return isNull(fromCache) ? null : fromCache.getSecond();
    }
}
