/*
 * RHQ Management Platform
 * Copyright (C) 2005-2008 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.rhq.enterprise.gui.admin.user;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import javax.ejb.EJBException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;

import org.rhq.core.domain.auth.Subject;
import org.rhq.core.domain.configuration.Configuration;
import org.rhq.enterprise.gui.legacy.Constants;
import org.rhq.enterprise.gui.legacy.WebUser;
import org.rhq.enterprise.gui.legacy.action.BaseAction;
import org.rhq.enterprise.gui.legacy.util.RequestUtils;
import org.rhq.enterprise.gui.legacy.util.SessionUtils;
import org.rhq.enterprise.server.RHQConstants;
import org.rhq.enterprise.server.auth.SubjectManagerLocal;
import org.rhq.enterprise.server.exception.LdapCommunicationException;
import org.rhq.enterprise.server.exception.LdapFilterException;
import org.rhq.enterprise.server.resource.group.LdapGroupManagerLocal;
import org.rhq.enterprise.server.system.SystemManagerLocal;
import org.rhq.enterprise.server.util.LookupUtil;

/**
 * Registers a user. Triggered when authenticated via LDAP.
 */
public class RegisterAction extends BaseAction {

    SubjectManagerLocal subjectManager = LookupUtil.getSubjectManager();
    SystemManagerLocal systemManager = LookupUtil.getSystemManager();
    LdapGroupManagerLocal ldapManager = LookupUtil.getLdapGroupManager();

    /**
     * Create the user with the attributes specified in the given <code>NewForm</code> and save it into the session
     * attribute <code>Constants.USER_ATTR</code>.
     *
     * @see BaseAction#execute(ActionMapping, ActionForm, HttpServletRequest, HttpServletResponse)
     */
    @Override
    public ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request,
        HttpServletResponse response) throws Exception {
        Log log = LogFactory.getLog(RegisterAction.class.getName());

        NewForm userForm = (NewForm) form;
        HttpSession session = request.getSession(false);

        ActionForward forward = checkSubmit(request, mapping, form);
        if (forward != null) {
            return forward;
        }

        WebUser webUser = SessionUtils.getWebUser(session);
        Subject newSubject = new Subject();

        newSubject.setName(webUser.getUsername());
        newSubject.setFirstName(userForm.getFirstName());
        newSubject.setLastName(userForm.getLastName());
        newSubject.setDepartment(userForm.getDepartment());
        newSubject.setEmailAddress(userForm.getEmailAddress());
        newSubject.setPhoneNumber(userForm.getPhoneNumber());
        newSubject.setSmsAddress(userForm.getSmsAddress());
        newSubject.setFactive(true);
        newSubject.setFsystem(false);

        // the new user has no prefs, but we still want to pick up the defaults
        Configuration userPrefs = (Configuration) getServlet().getServletContext().getAttribute(
            Constants.DEF_USER_PREFS);
        newSubject.setUserConfiguration(userPrefs);

        // password was saved off when the user logged in
        String password = (String) session.getAttribute(Constants.PASSWORD_SES_ATTR);
        session.removeAttribute(Constants.PASSWORD_SES_ATTR);

        Subject superuser = subjectManager.getOverlord();

        // create the subject, but don't add a principal since LDAP will handle authentication
        log.trace("registering new LDAP-authenticated subject [" + newSubject.getName() + "]");
        subjectManager.createSubject(superuser, newSubject);

        // nuke the temporary session and establish a new
        // one for this subject.. must be done before pulling the
        // new subject in order to do it with his own credentials
        subjectManager.logout(RequestUtils.getSessionId(request).intValue());
        newSubject = subjectManager.login(newSubject.getName(), password);

        // we also need to create up a new web user
        webUser = new WebUser(newSubject);
        SessionUtils.setWebUser(session, webUser);
        session.setAttribute(Constants.USER_OPERATIONS_ATTR, new HashMap()); // user has no permissions yet

        HashMap parms = new HashMap(1);
        parms.put(Constants.USER_PARAM, newSubject.getId());

        //BZ-580127: only do group authz check if one or both of group filter fields is set
        Properties options = systemManager.getSystemConfiguration();
        String groupFilter = (String) options.getProperty(RHQConstants.LDAPGroupFilter, "");
        String groupMember = (String) options.getProperty(RHQConstants.LDAPGroupMember, "");
        if ((groupFilter.trim().length() > 0) || (groupMember.trim().length() > 0)) {
            try { //defend against ldap communication runtime difficulties.
                String provider = LookupUtil.getSystemManager().getSystemConfiguration().getProperty(
                    RHQConstants.JAASProvider);
                if (RHQConstants.LDAPJAASProvider.equals(provider)) {
                    List<String> groupNames = new ArrayList(ldapManager.findAvailableGroupsFor(newSubject.getName()));
                    ldapManager.assignRolesToLdapSubject(newSubject.getId(), groupNames);
                }
            } catch (EJBException ejx) {
                //this is the exception type thrown now that we use SLSB.Local methods
                // mine out other exceptions
                Exception cause = ejx.getCausedByException();
                if (cause == null) {
                    ActionMessages actionMessages = new ActionMessages();
                    actionMessages.add(ActionMessages.GLOBAL_MESSAGE, new ActionMessage("errors.cam.general"));
                    saveErrors(request, actionMessages);
                } else {
                    if (cause instanceof LdapFilterException) {
                        ActionMessages actionMessages = new ActionMessages();
                        actionMessages.add(ActionMessages.GLOBAL_MESSAGE, new ActionMessage(
                            "admin.role.LdapGroupFilterMessage"));
                        saveErrors(request, actionMessages);
                    } else if (cause instanceof LdapCommunicationException) {
                        ActionMessages actionMessages = new ActionMessages();
                        SystemManagerLocal manager = LookupUtil.getSystemManager();
                        options = manager.getSystemConfiguration();
                        String providerUrl = options.getProperty(RHQConstants.LDAPUrl, "(unavailable)");
                        actionMessages.add(ActionMessages.GLOBAL_MESSAGE, new ActionMessage(
                            "admin.role.LdapCommunicationMessage", providerUrl));
                        saveErrors(request, actionMessages);
                    }
                }
            } catch (LdapFilterException lce) {
                ActionMessages actionMessages = new ActionMessages();
                actionMessages.add(ActionMessages.GLOBAL_MESSAGE,
                    new ActionMessage("admin.role.LdapGroupFilterMessage"));
                saveErrors(request, actionMessages);
            } catch (LdapCommunicationException lce) {
                ActionMessages actionMessages = new ActionMessages();
                String providerUrl = options.getProperty(RHQConstants.LDAPUrl, "(unavailable)");
                actionMessages.add(ActionMessages.GLOBAL_MESSAGE, new ActionMessage(
                    "admin.role.LdapCommunicationMessage", providerUrl));
                saveErrors(request, actionMessages);
            }
        }
        return returnSuccess(request, mapping, parms, false);
    }
}