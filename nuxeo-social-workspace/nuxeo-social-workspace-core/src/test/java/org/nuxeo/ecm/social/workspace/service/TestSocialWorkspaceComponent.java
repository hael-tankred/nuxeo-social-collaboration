/*
 * (C) Copyright 2006-2011 Nuxeo SAS (http://nuxeo.com/) and others.
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
 *     Nuxeo - initial API and implementation
 */
package org.nuxeo.ecm.social.workspace.service;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.inject.Inject;
import org.junit.Test;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.social.workspace.AbstractSocialWorkspaceTest;
import org.nuxeo.ecm.social.workspace.adapters.SocialWorkspace;

public class TestSocialWorkspaceComponent extends AbstractSocialWorkspaceTest {

    @Inject
    SocialWorkspaceService socialWorkspaceService;

    @Test
    public void testAddSeveralSocialWorkspaceMembers() throws Exception {
        SocialWorkspace socialWorkspace = createSocialWorkspace("Social workspace for test");

        String userAlreadyMember1Email = "userAlreadyMember1@mail.net";
        DocumentModel userAlreadyMember1 = createUserForTest(
                userAlreadyMember1Email, "userAlreadyMember1");
        socialWorkspace.addMember(userManager.getPrincipal(userAlreadyMember1.getId()));

        DocumentModel userAlreadyMember2 = createUserForTest(
                "userAlreadyMember2@mail.net", "userAlreadyMember2");
        socialWorkspace.addMember(userManager.getPrincipal(userAlreadyMember2.getId()));

        String userNewMember1Email = "userNewMember1@mail.net";
        createUserForTest(userNewMember1Email, "userNewMember1");
        createUserForTest("userNewMember2@mail.net", "userNewMember2");

        String nonExsitingUser1Email = "nonExistingUser1@mail.net";
        List<String> emails = Arrays.asList(userAlreadyMember1Email,
                "userAlreadyMember2@mail.net", "userNewMember1@mail.net",
                "userNewMember2@mail.net", nonExsitingUser1Email,
                "nonExistingUser2@mail.net");

        List<String> addedUsers = socialWorkspaceService.addSeveralSocialWorkspaceMembers(
                socialWorkspace, emails);
        assertEquals(2, addedUsers.size());
        assertFalse(addedUsers.contains(userAlreadyMember1Email));
        assertFalse(addedUsers.contains(nonExsitingUser1Email));
        assertTrue(addedUsers.contains(userNewMember1Email));

        addedUsers = socialWorkspaceService.addSeveralSocialWorkspaceMembers(
                socialWorkspace, emails);
        assertTrue(addedUsers.isEmpty());

        addedUsers = socialWorkspaceService.addSeveralSocialWorkspaceMembers(
                socialWorkspace, new ArrayList<String>());
        assertTrue(addedUsers.isEmpty());
    }

    @Test(expected = ClientException.class)
    public void testAddSeveralSocialWorkspaceMembersFromGroup()
            throws Exception {
        String existingUser1 = "userAlreadyMember1";
        String existingUser2 = "userAlreadyMember2";

        DocumentModel group1 = userManager.getBareGroupModel();
        group1.setPropertyValue(userManager.getGroupSchemaName() + ":"
                + userManager.getGroupIdField(), "group1");
        List<String> members = Arrays.asList(existingUser1, existingUser2,
                "Administrator", "unknown");
        group1.setProperty(userManager.getGroupSchemaName(),
                userManager.getGroupMembersField(), members);

        group1 = userManager.createGroup(group1);
        members = (List<String>) group1.getPropertyValue(userManager.getGroupSchemaName()
                + ":" + userManager.getGroupMembersField());
        assertEquals(4, members.size());

        SocialWorkspace sw = createSocialWorkspace("SocialWorkspaceWithGroup");
        assertEquals(1, sw.getMembers().size());

        List<String> imported = socialWorkspaceService.addSeveralSocialWorkspaceMembers(
                sw, group1.getName());
        assertEquals(2, imported.size());
        assertTrue(imported.contains(existingUser1));
        assertTrue(imported.contains(existingUser2));
        assertFalse(imported.contains("Administrator"));

        assertEquals(3, sw.getMembers().size());

        // Will throw a ClientException
        socialWorkspaceService.addSeveralSocialWorkspaceMembers(sw,
                "john_doe_group");
    }

    protected DocumentModel createUserForTest(String userEmail, String userId)
            throws ClientException {
        DocumentModel user = userManager.getBareUserModel();
        user.setPropertyValue(userManager.getUserSchemaName() + ":"
                + userManager.getUserEmailField(), userEmail);
        user.setPropertyValue(userManager.getUserSchemaName() + ":"
                + userManager.getUserIdField(), userId);

        return userManager.createUser(user);
    }

}
