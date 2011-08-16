/*
 * (C) Copyright 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Thomas Roger <troger@nuxeo.com>
 */

package org.nuxeo.ecm.social.mini.message;

import static org.nuxeo.ecm.social.mini.message.MiniMessageActivityStreamFilter.ACTOR_PARAMETER;
import static org.nuxeo.ecm.social.mini.message.MiniMessageActivityStreamFilter.QUERY_TYPE_PARAMETER;
import static org.nuxeo.ecm.social.mini.message.MiniMessageActivityStreamFilter.QueryType.MINI_MESSAGES_FOR_ACTOR;
import static org.nuxeo.ecm.social.mini.message.MiniMessageActivityStreamFilter.QueryType.MINI_MESSAGES_FROM_ACTOR;
import static org.nuxeo.ecm.social.mini.message.MiniMessageActivityStreamFilter.VERB;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.nuxeo.ecm.activity.Activity;
import org.nuxeo.ecm.activity.ActivityImpl;
import org.nuxeo.ecm.activity.ActivityStreamService;
import org.nuxeo.ecm.core.api.ClientRuntimeException;
import org.nuxeo.ecm.social.user.relationship.RelationshipKind;
import org.nuxeo.runtime.api.Framework;

/**
 * Default implementation of {@link MiniMessageService}.
 *
 * @author <a href="mailto:troger@nuxeo.com">Thomas Roger</a>
 * @since 5.4.3
 */
public class MiniMessageServiceImpl implements MiniMessageService {

    protected ActivityStreamService activityStreamService;

    @Override
    public MiniMessage addMiniMessage(String actor, String message,
            Date publishedDate) {
        Activity activity = new ActivityImpl();
        activity.setVerb(VERB);
        activity.setActor(actor);
        activity.setObject(message);
        activity.setPublishedDate(publishedDate);
        activity = getActivityStreamService().addActivity(activity);
        return MiniMessage.fromActivity(activity);
    }

    @Override
    public MiniMessage addMiniMessage(String actor, String message) {
        return addMiniMessage(actor, message, new Date());
    }

    @Override
    public List<MiniMessage> getMiniMessageFor(String actor,
            RelationshipKind relationshipKind, int pageSize, int currentPage) {
        Map<String, Serializable> parameters = new HashMap<String, Serializable>();
        parameters.put(ACTOR_PARAMETER, actor);
        parameters.put(QUERY_TYPE_PARAMETER, MINI_MESSAGES_FOR_ACTOR);
        List<Activity> activities = getActivityStreamService().query(
                MiniMessageActivityStreamFilter.ID, parameters, pageSize,
                currentPage);

        List<MiniMessage> miniMessages = new ArrayList<MiniMessage>();
        for (Activity activity : activities) {
            miniMessages.add(MiniMessage.fromActivity(activity));
        }
        return miniMessages;
    }

    @Override
    public List<MiniMessage> getMiniMessageFrom(String actor, int pageSize,
            int currentPage) {
        Map<String, Serializable> parameters = new HashMap<String, Serializable>();
        parameters.put(ACTOR_PARAMETER, actor);
        parameters.put(QUERY_TYPE_PARAMETER, MINI_MESSAGES_FROM_ACTOR);
        List<Activity> activities = getActivityStreamService().query(
                MiniMessageActivityStreamFilter.ID, parameters, pageSize,
                currentPage);

        List<MiniMessage> miniMessages = new ArrayList<MiniMessage>();
        for (Activity activity : activities) {
            miniMessages.add(MiniMessage.fromActivity(activity));
        }
        return miniMessages;
    }

    private ActivityStreamService getActivityStreamService()
            throws ClientRuntimeException {
        if (activityStreamService == null) {
            try {
                activityStreamService = Framework.getService(ActivityStreamService.class);
            } catch (Exception e) {
                final String errMsg = "Error connecting to ActivityStreamService. "
                        + e.getMessage();
                throw new ClientRuntimeException(errMsg, e);
            }
            if (activityStreamService == null) {
                throw new ClientRuntimeException(
                        "ActivityStreamService service not bound");
            }
        }
        return activityStreamService;
    }

}