/*
 * (C) Copyright 2010 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 * Contributors:
 * Nuxeo - initial API and implementation
 */

package org.nuxeo.ecm.social.workspace.listeners;

import static org.nuxeo.ecm.social.workspace.SocialConstants.NEWS_SECTION_NAME;
import static org.nuxeo.ecm.social.workspace.SocialConstants.PUBLIC_NEWS_SECTION_NAME;
import static org.nuxeo.ecm.social.workspace.SocialConstants.ROOT_SECTION_NAME;
import static org.nuxeo.ecm.social.workspace.SocialConstants.SOCIAL_WORKSPACE_ACL_NAME;
import static org.nuxeo.ecm.social.workspace.SocialConstants.SOCIAL_WORKSPACE_FACET;

import java.util.Arrays;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.event.DocumentEventTypes;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.ACP;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.core.api.security.impl.ACPImpl;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.ecm.platform.usermanager.exceptions.GroupAlreadyExistsException;
import org.nuxeo.ecm.social.workspace.SocialWorkspaceHelper;
import org.nuxeo.runtime.api.Framework;

/**
 * Class to handle community document creation. It automatically creates two
 * groups :
 * <ul>
 * <li>{doc_id}_administrators : with an EVERYTHING permission</li>
 * <li>{doc_id}_members : with a READ_WRITE permission</li>
 * </ul>
 *
 * @author <a href="mailto:akervern@nuxeo.com">Arnaud Kervern</a>
 * @since 5.4.1
 */
public class CreateSocialWorkspaceGroupListener implements EventListener {

    protected UserManager userManager;

    private static final Log log = LogFactory.getLog(CreateSocialWorkspaceGroupListener.class);

    @Override
    public void handleEvent(Event event) throws ClientException {
        if (!event.getName().equals(DocumentEventTypes.DOCUMENT_CREATED)) {
            return;
        }

        if (!(event.getContext() instanceof DocumentEventContext)) {
            return;
        }

        DocumentEventContext ctx = (DocumentEventContext) event.getContext();
        DocumentModel doc = ctx.getSourceDocument();

        if (!doc.hasFacet(SOCIAL_WORKSPACE_FACET)) {
            return;
        }
        ACP acp = doc.getACP();
        acp.addACL(createCommunityACL(acp, doc));
        doc.setACP(acp, true);

        CoreSession session = ctx.getCoreSession();
        session.saveDocument(doc);

        handleACPOnSocialSections(doc, session);

        createGroup(
                SocialWorkspaceHelper.getCommunityAdministratorsGroupName(doc),
                SocialWorkspaceHelper.getCommunityAdministratorsGroupLabel(doc),
                ctx.getPrincipal().getName());
        createGroup(SocialWorkspaceHelper.getCommunityMembersGroupName(doc),
                SocialWorkspaceHelper.getCommunityMembersGroupLabel(doc),
                null);
    }

    /**
     * Create a group, and if exists add the principal as a member.
     *
     * @param groupName group name you want to create
     * @param groupLabel group label that can be null
     * @param principal null is you do not want to add a member
     */
    protected void createGroup(String groupName, String groupLabel, String principal) throws ClientException{
        UserManager userManager = getUserManager();
        try {
            String groupSchemaName = userManager.getGroupSchemaName();

            DocumentModel group = userManager.getBareGroupModel();
            group.setProperty(groupSchemaName, userManager.getGroupIdField(),
                    groupName);
            group.setProperty(groupSchemaName, userManager.getGroupLabelField(),
                    groupLabel);
            group = userManager.createGroup(group);

            if (!(principal == null || "".equals(principal))) {
                group.setProperty(groupSchemaName, userManager.getGroupMembersField(),
                        Arrays.asList(principal));
            }
            userManager.updateGroup(group);
        } catch (GroupAlreadyExistsException e) {
            log.info("Group already exists : " + groupName);
        } catch (ClientException e) {
            log.error("Cannot create group " + groupName, e);
        }
    }

    protected ACL createCommunityACL(ACP acp, DocumentModel doc) {
        ACL acl = acp.getOrCreateACL(SOCIAL_WORKSPACE_ACL_NAME);
        acl.add(new ACE(
                SocialWorkspaceHelper.getCommunityAdministratorsGroupName(doc),
                SecurityConstants.EVERYTHING, true));
        acl.add(new ACE(
                SocialWorkspaceHelper.getCommunityMembersGroupName(doc),
                SecurityConstants.READ_WRITE, true));
        return acl;
    }

    protected UserManager getUserManager() throws ClientException {
        if (userManager == null) {
            try {
                userManager = Framework.getService(UserManager.class);
            } catch (Exception e) {
                log.error("Cannot instantiate userManager", e);
                throw new ClientException(e);
            }
        }
        return userManager;
    }

    public void handleACPOnSocialSections(DocumentModel socialWorkspace,
            CoreSession session) throws ClientException {
        DocumentModel rootSocialSection = session.getChild(
                socialWorkspace.getRef(), ROOT_SECTION_NAME);

        tuneRightsOnSocialSection(socialWorkspace, session, rootSocialSection);

        DocumentRef privatNewsSectionRef = new PathRef(
                rootSocialSection.getPathAsString(), NEWS_SECTION_NAME);

        DocumentModel publicSocialSection = session.getChild(
                privatNewsSectionRef, PUBLIC_NEWS_SECTION_NAME);

        grantReadRightForEveryOneOnSocialSection(socialWorkspace, session,
                publicSocialSection);
    }

    protected void tuneRightsOnSocialSection(DocumentModel socialWorkspace,
            CoreSession session, DocumentModel rootSocialSection)
            throws ClientException {
        ACP acp = new ACPImpl();
        ACL acl = acp.getOrCreateACL(SOCIAL_WORKSPACE_ACL_NAME);
        grantSpecificRightsOnSocialSections(socialWorkspace, acl);
        acl.add(new ACE(SecurityConstants.EVERYONE,
                SecurityConstants.EVERYTHING, false));
        acp.addACL(acl);
        rootSocialSection.setACP(acp, true);
        session.saveDocument(rootSocialSection);
    }

    protected void grantSpecificRightsOnSocialSections(
            DocumentModel socialWorkspace, ACL acl) throws ClientException {
        grantEverythingToAdministrator(acl);
        acl.add(new ACE(
                SocialWorkspaceHelper.getCommunityAdministratorsGroupName(socialWorkspace),
                SecurityConstants.EVERYTHING, true));
        acl.add(new ACE(
                SocialWorkspaceHelper.getCommunityMembersGroupName(socialWorkspace),
                SecurityConstants.READ_WRITE, true));

    }

    protected void grantEverythingToAdministrator(ACL acl)
            throws ClientException {
        for (String adminGroup : getUserManager().getAdministratorsGroups()) {
            acl.add(new ACE(adminGroup, SecurityConstants.EVERYTHING, true));
        }
    }

    protected void grantReadRightForEveryOneOnSocialSection(
            DocumentModel socialWorkspace, CoreSession session,
            DocumentModel publicSocialSection) throws ClientException {

        ACP acpPublicSection = new ACPImpl();

        ACL aclPublicSection = acpPublicSection.getOrCreateACL(SOCIAL_WORKSPACE_ACL_NAME);
        grantSpecificRightsOnSocialSections(socialWorkspace, aclPublicSection);
        grantPublicReadRight(aclPublicSection);

        acpPublicSection.addACL(aclPublicSection);
        publicSocialSection.setACP(acpPublicSection, true);

        session.saveDocument(publicSocialSection);
    }

    protected void grantPublicReadRight(ACL aclPublicSection)
            throws ClientException {
        String defaultGroupName = getUserManager().getDefaultGroup();
        if (defaultGroupName == null ) {
            defaultGroupName = SecurityConstants.EVERYONE;
        }
        aclPublicSection.add(new ACE(defaultGroupName, SecurityConstants.READ,
                true));
    }

}
