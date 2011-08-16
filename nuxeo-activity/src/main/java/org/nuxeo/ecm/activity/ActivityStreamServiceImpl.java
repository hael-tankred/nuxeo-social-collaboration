/*
 * (C) Copyright 2011 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Thomas Roger <troger@nuxeo.com>
 */

package org.nuxeo.ecm.activity;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.ClientRuntimeException;
import org.nuxeo.ecm.core.persistence.PersistenceProvider;
import org.nuxeo.ecm.core.persistence.PersistenceProviderFactory;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

/**
 * Default implementation of {@link ActivityStreamService}.
 *
 * @author <a href="mailto:troger@nuxeo.com">Thomas Roger</a>
 * @since 5.4.3
 */
public class ActivityStreamServiceImpl extends DefaultComponent implements
        ActivityStreamService {

    private static final Log log = LogFactory.getLog(ActivityStreamServiceImpl.class);

    public static final String ACTIVITIES_PROVIDER = "nxactivities";

    public static final String ACTIVITY_STREAM_FILTER_EP = "activityStreamFilters";

    protected final ThreadLocal<EntityManager> localEntityManager = new ThreadLocal<EntityManager>();

    protected final Map<String, ActivityStreamFilter> activityStreamFilters = new HashMap<String, ActivityStreamFilter>();

    protected PersistenceProvider persistenceProvider;

    @Override
    public List<Activity> query(String filterId,
            final Map<String, Serializable> parameters) {
        return query(filterId, parameters, 0, 0);
    }

    @Override
    public List<Activity> query(String filterId,
            final Map<String, Serializable> parameters, final int pageSize,
            final int currentPage) {
        if (ALL_ACTIVITIES.equals(filterId)) {
            return queryAllByPage(pageSize, currentPage);
        }

        final ActivityStreamFilter filter = activityStreamFilters.get(filterId);
        if (filter == null) {
            throw new ClientRuntimeException(String.format(
                    "Unable to retrieve '%s' ActivityStreamFilter", filterId));
        }

        return query(filter, parameters, pageSize, currentPage);
    }

    protected List<Activity> query(final ActivityStreamFilter filter,
            final Map<String, Serializable> parameters, final int pageSize,
            final int currentPage) {
        try {
            return getOrCreatePersistenceProvider().run(false,
                    new PersistenceProvider.RunCallback<List<Activity>>() {
                        public List<Activity> runWith(EntityManager em) {
                            return query(em, filter, parameters, pageSize,
                                    currentPage);
                        }
                    });
        } catch (ClientException e) {
            throw new ClientRuntimeException(e);
        }
    }

    protected List<Activity> query(EntityManager em,
            ActivityStreamFilter filter, Map<String, Serializable> parameters,
            int pageSize, int currentPage) {
        try {
            localEntityManager.set(em);
            return filter.query(this, parameters, pageSize, currentPage);
        } finally {
            localEntityManager.remove();
        }

    }

    protected List<Activity> queryAllByPage(final int pageSize,
            final int currentPage) {
        try {
            return getOrCreatePersistenceProvider().run(false,
                    new PersistenceProvider.RunCallback<List<Activity>>() {
                        public List<Activity> runWith(EntityManager em) {
                            return queryAllByPage(em, pageSize, currentPage);
                        }
                    });
        } catch (ClientException e) {
            throw new ClientRuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    protected List<Activity> queryAllByPage(EntityManager em, int pageSize, int currentPage) {
        Query query = em.createQuery("from Activity activity");
        if (pageSize > 0) {
            query.setMaxResults(pageSize);
            if (currentPage > 0) {
                query.setFirstResult((currentPage - 1) * pageSize);
            }
        }
        return query.getResultList();
    }

    @Override
    public Activity addActivity(final Activity activity) {
        try {
            getOrCreatePersistenceProvider().run(true,
                    new PersistenceProvider.RunVoid() {
                        public void runWith(EntityManager em) {
                            addActivity(em, activity);
                        }
                    });
        } catch (ClientException e) {
            throw new ClientRuntimeException(e);
        }
        return activity;
    }

    protected void addActivity(EntityManager em, Activity activity) {
        try {
            localEntityManager.set(em);
            em.persist(activity);
            for (ActivityStreamFilter filter : activityStreamFilters.values()) {
                if (filter.isInterestedIn(activity)) {
                    filter.handleNewActivity(this, activity);
                }
            }
        } finally {
            localEntityManager.remove();
        }
    }

    public EntityManager getEntityManager() {
        return localEntityManager.get();
    }

    public PersistenceProvider getOrCreatePersistenceProvider() {
        if (persistenceProvider == null) {
            activatePersistenceProvider();
        }
        return persistenceProvider;
    }

    protected void activatePersistenceProvider() {
        Thread thread = Thread.currentThread();
        ClassLoader last = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(PersistenceProvider.class.getClassLoader());
            PersistenceProviderFactory persistenceProviderFactory = Framework.getLocalService(PersistenceProviderFactory.class);
            persistenceProvider = persistenceProviderFactory.newProvider(ACTIVITIES_PROVIDER);
            persistenceProvider.openPersistenceUnit();
        } finally {
            thread.setContextClassLoader(last);
        }
    }

    protected void deactivatePersistenceProvider() {
        if (persistenceProvider != null) {
            persistenceProvider.closePersistenceUnit();
            persistenceProvider = null;
        }
    }

    @Override
    public void deactivate(ComponentContext context) throws Exception {
        deactivatePersistenceProvider();
        super.deactivate(context);
    }

    @Override
    public void registerContribution(Object contribution,
            String extensionPoint, ComponentInstance contributor)
            throws Exception {
        if (ACTIVITY_STREAM_FILTER_EP.equals(extensionPoint)) {
            ActivityStreamFilterDescriptor descriptor = (ActivityStreamFilterDescriptor) contribution;
            ActivityStreamFilter filter = descriptor.getActivityStreamFilter();

            String filterId = filter.getId();

            boolean enabled = descriptor.isEnabled();
            if (activityStreamFilters.containsKey(filterId)) {
                log.info("Overriding activity stream filter with id "
                        + filterId);
                if (!enabled) {
                    activityStreamFilters.remove(filterId);
                    log.info("Disabled activity stream filter with id "
                            + filterId);
                }
            }
            if (enabled) {
                log.info("Registering activity stream filter with id "
                        + filterId);
                activityStreamFilters.put(filterId,
                        descriptor.getActivityStreamFilter());
            }
        }
    }

    @Override
    public void unregisterContribution(Object contribution,
            String extensionPoint, ComponentInstance contributor)
            throws Exception {
        if (ACTIVITY_STREAM_FILTER_EP.equals(extensionPoint)) {
            ActivityStreamFilterDescriptor descriptor = (ActivityStreamFilterDescriptor) contribution;
            ActivityStreamFilter filter = descriptor.getActivityStreamFilter();
            String filterId = filter.getId();
            activityStreamFilters.remove(filterId);
            log.info("Unregistering activity stream filter with id " + filterId);
        }
    }

}